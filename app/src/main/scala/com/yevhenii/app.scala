package com.yevhenii

import java.time.format.DateTimeFormatter

import com.cybozu.labs.langdetect.DetectorFactory
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import scala.util.Properties

object Application {
  def main(args: Array[String]): Unit ={
    val kafkaAddress = Properties.envOrElse("KAFKA_ADDRESS", "")
    val kafkaTopic = Properties.envOrElse("KAFKA_TOPIC", "")


    val appName = "HDFSData"
    val conf = new SparkConf()
    conf.setAppName(appName).setMaster("local[2]")
      .set("spark.jars.packages", "org.apache.spark:spark-sql-kafka-0-10:2.4.0")

    val spark = SparkSession.builder.config(conf).getOrCreate();

    val df = spark
      .read
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaAddress)
      .option("subscribe", kafkaTopic)
      .load()

    df.writeStream.format("console").start().awaitTermination()
  }
}