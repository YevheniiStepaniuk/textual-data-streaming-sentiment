name := "TextualDataStreamingSentiment"
version := "1.0"
scalaVersion := "2.11.12"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.0"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.4.0"
libraryDependencies += "org.apache.spark" %% "spark-streaming" % "2.4.0"
libraryDependencies += "org.apache.spark" %% "spark-sql-kafka-0-10" % "2.4.0" 
libraryDependencies += "org.apache.spark" %% "spark-streaming-kafka" % "1.6.3"

libraryDependencies += "org.elasticsearch" %% "elasticsearch-spark" % "2.4.5"

libraryDependencies += "com.cybozu.labs" % "langdetect" % "1.1-20120112"


libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11"

resolvers += "cloudera-repos" at "https://repository.cloudera.com/artifactory/cloudera-repos/"
resolvers += "Akka Repository" at "http://repo.akka.io/releases/"


mainClass in assembly := Some("com.yevhenii")


assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}