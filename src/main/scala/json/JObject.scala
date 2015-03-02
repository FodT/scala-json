package json

import scala.collection.generic.{ CanBuildFrom, GenericCompanion }
import scala.collection.mutable.Builder
import scala.collection.immutable.{ MapLike, VectorBuilder }
import java.util.UUID
import scala.collection.IterableLike

object JObject extends GenericCompanion[scala.collection.immutable.Iterable] {
  //TODO: allows duplicates...
  def apply(values: (JString, JValue)*): JObject = {
    val keyList = values.map(_._1)
    val keySet = keyList.toSet

    require(keyList.length == keySet.size, "duplicate keys!")

    JObject(values.toMap)(keyList)
  }

  def apply[T](obj: T)(implicit accessor: ObjectAccessor[T]): JObject =
    accessor.createJSON(obj).toJObject

  def newCanBuildFrom = new CanBuildFrom[TraversableOnce[(JString, JValue)], (JString, JValue), JObject] {
    def apply(from: TraversableOnce[(JString, JValue)]) = newJObjectBuilder // ++= from
    def apply() = newJObjectBuilder
  }

  lazy val empty = apply()

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[(JString, JValue)], (JString, JValue), JObject] =
    newCanBuildFrom

  def newJObjectBuilder: Builder[(JString, JValue), JObject] = new JObjectBuilder

  class JObjectBuilder extends Builder[(JString, JValue), JObject] {
    val builder = new VectorBuilder[(JString, JValue)]

    def +=(item: (JString, JValue)): this.type = {
      builder += item
      this
    }

    def result: JObject = {
      JObject(builder.result: _*)
    }

    def clear() {
      builder.clear
    }
  }

  def newBuilder[A]: Builder[A, scala.collection.immutable.Iterable[A]] =
    scala.collection.immutable.Iterable.newBuilder
}

final case class JObject(override val fields: Map[JString, JValue])(
  implicit val keyIterable: Iterable[JString] = fields.map(_._1)) extends JValue
    //with Map[JString, JValue] with MapLike[JString, JValue, JObject] {
    with Iterable[(JString, JValue)] with IterableLike[(JString, JValue), JObject] {

  lazy val uuid = UUID.randomUUID.toString
  //lazy val keyIterator: Iterator[JString] = _keyIterator

  def keyIterator = keyIterable.iterator

  def empty = JObject.empty

  def default(key: JString): JValue = JUndefined

  override def values: Iterable[JValue] = fields.values
  def value = fields.map(pair => pair._1.str -> pair._2.value)

  override def apply(keyV: JValue) = {
    val key = keyV.toJString

    get(key).getOrElse(default(key))
  }

  def get(key: JString): Option[JValue] = fields.get(key)

  def iterator: Iterator[(JString, JValue)] = keyIterator map { k =>
    k -> apply(k: JValue)
  }

  def +[B1 >: JValue](kv: (JString, B1)): JObject = {
    val thisMap = fields

    val (key, v) = kv

    val newMap = (thisMap + kv).asInstanceOf[Map[JString, JValue]]

    //append new keys to end
    if (thisMap.get(key).isDefined)
      new JObject(newMap)(keyIterable)
    else {
      val seq = keyIterable ++ Seq(key)
      new JObject(newMap)(seq)
    }
  }

  def -(key: JString): JObject = {
    val newKeys = keyIterable.filter(_ != key)

    new JObject(fields - key)(newKeys)
  }

  /*override def -(key: Any): JValue =
		this - JValue.from(key).toJString.str*/

  def toJString: JString =
    sys.error("cannot convert JObject to JString")
  //JString("object " + uuid) //this... should be different
  def toJNumber: JNumber = JNaN
  def toJBoolean: JBoolean = JTrue

  override def jValue = this
  override def keys: Iterable[JString] = keyIterable
  /*def toMap: Map[JString, JValue] = keys map { key =>
		val jstr = key.toJString
		(jstr, getJObjectValue(jstr))
	} toMap*/

  override def companion: GenericCompanion[scala.collection.immutable.Iterable] = JObject
  override def newBuilder = JObject.newJObjectBuilder

  override def isObject = true

  override def toJObject: JObject = this

  override def jObject: JObject = this
  override def jArray: JArray = throw GenericJSONException("Expected JArray")
  override def jNumber: JNumber = throw GenericJSONException("Expected JNumber")
  override def jString: JString = throw GenericJSONException("Expected JString")
  override def jBoolean: JBoolean = throw GenericJSONException("Expected JBoolean")

  override def toString = toJSONString

  def ++(that: JObject): JObject = {
    val thisMap = toMap
    val thatMap = that.toMap
    val thisSeq = keyIterator.toSeq
    val thisKeySet = thisMap.keySet
    val newKeys = that.keyIterator.toSeq
    val appendSeq = newKeys.filter(!thisKeySet(_))

    JObject(thisMap ++ thatMap)(thisSeq ++ appendSeq)
  }

  def toJSONStringBuilder(settings: JSONBuilderSettings,
    lvl: Int): StringBuilder = {
    val out = new StringBuilder

    val nl = settings.newLineString
    val tab = settings.tabString

    def tabs = out append settings.nTabs(lvl)

    out append ("{" + nl)

    var isFirst = true
    keyIterator foreach { key =>
      val v = apply(key: JValue)
      if ((v !== JUndefined) && (!settings.ignoreNulls || (v !== JNull))) {
        if (!isFirst) out.append("," + settings.spaceString + nl)

        tabs append tab
        out append key.toJSONStringBuilder(settings)
        out append (":" + settings.spaceString)
        out append v.toJSONStringBuilder(settings, lvl + 1)

        isFirst = false
      }
    }

    out append "}"
  }
}