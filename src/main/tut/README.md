Getting Started
===============

* Import the json package
```tut
import json._
JValue fromString "[1,2,3,4,5]"
```
* Implicit conversion to JValue types
```tut
"hello".js
def testMap = Map("hey" -> "there")
val testMapJs = testMap.js
Seq.fill(3)(testMap).js.toDenseString
testMap.keySet.headOption.js
testMap.get("nokey").js
```
* Use JValues dynamically
```tut
require(testMapJs("nokey") == JUndefined)
```

SBT
---

```scala

resolvers += "mmreleases" at "https://artifactory.mediamath.com/artifactory/libs-release-global"

libraryDependencies += "com.mediamath" %% "scala-json" % "0.1"

```

and for scala.js

```scala

libraryDependencies += "com.mediamath" %%% "scala-json" % "0.1"

```