package json.internal

import java.text.SimpleDateFormat

import json._

import scala.collection.generic.CanBuildFrom

trait Accessors {
  import json.ObjectAccessor.manifestOf

  def accessorFor[T](implicit acc: JSONAccessor[T]) = acc

  implicit def optionAccessor[T, U <: Option[T]](
    implicit acc: JSONAccessor[T]) =
    new JSONAccessorProducer[Option[T], JValue] {
      lazy val manifest = manifestOf[Option[Any]].asInstanceOf[Manifest[Option[T]]]

      def createJSON(obj: Option[T]): JValue = obj match {
        case Some(x) => x.js
        case _       => JNull
      }

      def fromJSON(js: JValue): Option[T] = js match {
        case JUndefined => None
        case JNull      => None
        case x          => Some(x.to(acc))
      }

      override def createSwaggerProperty: JObject = {
        acc.createSwaggerProperty + (JString("required") -> JFalse)
      }

      override def extraSwaggerModels: Seq[JObject] = acc match {
        case x: CaseClassObjectAccessor[_] => x.createSwaggerModels
        case _                             => Nil
      }
    }

  implicit def mapAccessor[K, T](implicit acc: JSONAccessor[T],
    keyAcc: JSONProducer[K, JString] with JSONReader[K]) =
    new JSONAccessorProducer[Map[K, T], JObject] {
      lazy val manifest = manifestOf[Map[Any, Any]].asInstanceOf[Manifest[Map[K, T]]]

      val swaggerModelName = s"Map[String,${acc.runtimeClass.getSimpleName}]"

      def createJSON(obj: Map[K, T]): JObject = JObject(obj map {
        case (k, v) =>
          k.js -> v.js
      })

      def fromJSON(js: JValue): Map[K, T] = js match {
        case JObject(fields) =>
          var exceptions = List[InputFormatException]()

          val res = fields flatMap {
            case (k, v) =>
              try Seq(k.toObject[K] -> v.to[T]) catch {
                case e: InputFormatException =>
                  exceptions ::= e.prependFieldName(k.str)
                  Nil
              }
          }

          if (!exceptions.isEmpty)
            throw InputFormatsException(exceptions.flatMap(_.getExceptions).toSet[InputFormatException])

          res
        case x => throw InputTypeException("",
          "object", x.getClass.getName, x)
      }

      override def extraSwaggerModels: Seq[JObject] = {

        val typ = acc.runtimeClass.getSimpleName

        val jstr = s"""{
					"id": "$swaggerModelName",
					"description": "Key value pair of String -> $typ",
					"properties": {
						"{key}": {
							"type": "string",
							"required": true,
							"defaultValue": false
						},
						"{value}": {
							"type": "$typ",
							"required": true,
							"defaultValue": false
						}
					}
				}"""
        //println(jstr)
        JObject(swaggerModelName ->> jstr.jValue.toJObject) +: (acc match {
          case x: CaseClassObjectAccessor[_] => x.createSwaggerModels
          case _                             => Nil
        })
      }

      override def createSwaggerProperty: JObject = {
        JValue(Map("type" -> swaggerModelName)).toJObject
      }
    }

  implicit def iterableAccessor[T, U[T] <: Iterable[T]](
    implicit acc: JSONAccessor[T],
    cbf: CanBuildFrom[Nothing, T, U[T]]) = new JSONAccessorProducer[U[T], JArray] {
    lazy val manifest: Manifest[U[T]] = manifestOf[Iterable[Any]].asInstanceOf[Manifest[U[T]]]

    def createJSON(obj: U[T]): JArray = JArray(obj.map(_.js))

    def fromJSON(js: JValue): U[T] = js match {
      //TODO: append field names here...
      case JArray(vals) => //vals.iterator.map(_.to[T]).to[U](cbf)
        var exceptions = List[InputFormatException]()

        val res = vals.iterator.zipWithIndex.flatMap {
          case (x, idx) =>
            try Seq(x.to[T]) catch {
              case e: InputFormatException =>
                exceptions ::= e.prependFieldName(idx.toString)
                Nil
            }
        }.to[U](cbf)

        if (!exceptions.isEmpty)
          throw InputFormatsException(exceptions.flatMap(_.getExceptions).toSet)

        res
      case x => throw InputTypeException("",
        "array", x.getClass.getName, x)
    }

    override def createSwaggerProperty: JObject = {
      val unique = if (manifest.runtimeClass == classOf[Set[_]])
        JObject("uniqueItems" ->> JTrue)
      else JObject.empty

      JObject("type" ->> JString("array"), "items" ->> JObject(
        "$ref" ->> JString(acc.manifest.runtimeClass.getSimpleName)
      )) ++ unique
    }

    override def extraSwaggerModels: Seq[JObject] = acc match {
      case x: CaseClassObjectAccessor[_] => x.createSwaggerModels
      case _                             => Nil
    }
  }

  implicit case object BigDecimalAccessor extends JSONAccessorProducer[BigDecimal, JNumber] {
    val manifest = manifestOf[BigDecimal]

    def createJSON(obj: BigDecimal): JNumber = JNumber(obj.toDouble)
    def fromJSON(js: JValue): BigDecimal = js.toJNumber match {
      case jn @ JNumber(d) if !jn.isNaN => BigDecimal(d)
      case x => throw InputTypeException("",
        "numeric", x.getClass.getName, x)
    }
  }

  implicit case object DateAccessor extends JSONAccessorProducer[java.util.Date, JString] {
    val manifest = manifestOf[java.util.Date]

    def createJSON(obj: java.util.Date): JString = JString(obj.toString)
    def fromJSON(js: JValue): java.util.Date = js match {
      case str: JString =>
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(str.toString)
    }
  }

  implicit case object StringAccessor extends JSONAccessorProducer[String, JString] {
    val manifest = manifestOf[String]

    def createJSON(obj: String): JString = JString(obj)
    def fromJSON(js: JValue): String = js match {
      case JString(str) => str
      case x => throw InputTypeException("",
        "string", x.getClass.getName, x)
    }
  }

  implicit case object BooleanAccessor extends JSONAccessorProducer[Boolean, JBoolean] {
    val manifest = manifestOf[Boolean]

    def createJSON(obj: Boolean): JBoolean = JBoolean(obj)
    def fromJSON(js: JValue): Boolean = js match {
      case JFalse       => false
      case JTrue        => true
      case JString("0") => false
      case JString("1") => true
      case x => throw InputTypeException("",
        "boolean", x.getClass.getName, x)
    }
  }

  implicit case object IntAccessor extends JSONAccessorProducer[Int, JNumber] {
    val manifest = manifestOf[Int]

    def createJSON(obj: Int): JNumber = JNumber(obj)
    def fromJSON(js: JValue): Int = js.toJNumber match {
      case jn @ JNumber(d) if !jn.isNaN =>
        val nval = d.toInt
        if (nval.toDouble == d) nval
        else throw NumericTypeException("", d, "int")
      case x => throw InputTypeException("",
        "numeric", x.getClass.getName, x)
    }
  }

  implicit case object LongAccessor extends JSONAccessorProducer[Long, JNumber] {
    val manifest = manifestOf[Long]

    def createJSON(obj: Long): JNumber = JNumber(obj)
    def fromJSON(js: JValue): Long = js.toJNumber match {
      case jn @ JNumber(d) if !jn.isNaN =>
        val nval = d.toLong
        if (nval.toDouble == d) nval
        else throw NumericTypeException("", d, "long")
      case x => throw InputTypeException("",
        "numeric", x.getClass.getName, x)
    }
  }

  implicit case object DoubleAccessor extends JSONAccessorProducer[Double, JNumber] {
    val manifest = manifestOf[Double]

    def createJSON(obj: Double): JNumber = JNumber(obj)
    def fromJSON(js: JValue): Double = js.toJNumber match {
      case jn @ JNumber(d) if !jn.isNaN => d
      case x => throw InputTypeException("",
        "numeric", x.getClass.getName, x)
    }
  }

  implicit case object FloatAccessor extends JSONAccessorProducer[Float, JNumber] {
    val manifest = manifestOf[Float]

    def createJSON(obj: Float): JNumber = JNumber(obj)
    def fromJSON(js: JValue): Float = js.toJNumber match {
      case jn @ JNumber(d) if !jn.isNaN =>
        val nval = d.toFloat
        if (nval.toDouble == d) nval
        else throw NumericTypeException("", d, "float")
      case x => throw InputTypeException("",
        "numeric", x.getClass.getName, x)
    }
  }

  implicit case object ShortAccessor extends JSONAccessorProducer[Short, JNumber] {
    val manifest = manifestOf[Short]

    def createJSON(obj: Short): JNumber = JNumber(obj)
    def fromJSON(js: JValue): Short = js.toJNumber match {
      case jn @ JNumber(d) if !jn.isNaN =>
        val nval = d.toShort
        if (nval.toDouble == d) nval
        else throw NumericTypeException("", d, "short")
      case x => throw InputTypeException("",
        "numeric", x.getClass.getName, x)
    }
  }

  implicit case object JValueAccessor extends JSONAccessorProducer[JValue, JValue] {
    val manifest = manifestOf[JValue]

    def createJSON(obj: JValue): JValue = obj
    def fromJSON(js: JValue): JValue = js
  }

}