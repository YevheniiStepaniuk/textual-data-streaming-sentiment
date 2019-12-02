package com.yevhenii

import java.time.format.DateTimeFormatter
import com.cybozu.labs.langdetect.DetectorFactory
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import scala.util.Properties

import org.apache.spark.sql._
import org.apache.spark.sql.types._

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{from_json, split, col}
import org.apache.log4j.{Level, Logger}
// import spark.implicits._

object Application {
  def main(args: Array[String]): Unit ={
    Logger.getLogger("org").setLevel(Level.ERROR)

    val kafkaAddress = Properties.envOrElse("KAFKA_ADDRESS", "")
    val kafkaTopic = Properties.envOrElse("KAFKA_TOPIC", "")

    val appName = "HDFSData"
    val conf = new SparkConf()
    conf.setAppName(appName).setMaster("local[2]")
      .set("spark.jars.packages", "org.apache.spark:spark-sql-kafka-0-10:2.4.0")

    val spark = SparkSession.builder.config(conf).getOrCreate();

    val schema = new StructType()
    .add("message",StringType)
    .add("user_full_name",StringType)
    .add("from_username",StringType)
    .add("date",StringType)
    .add("chat_title",StringType)
    .add("chat_name",StringType)
    .add("chat_id",StringType)

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaAddress)
      .option("subscribe", kafkaTopic)
      .load()
      .select(from_json(col("value")
      .cast("string"), schema)
      .as("data"))
      .select("data.*")
      // .map(row => row.get(0))

      df.writeStream
      .format("console")
      .outputMode("append")
      .start()
      .awaitTermination()
  }
}
