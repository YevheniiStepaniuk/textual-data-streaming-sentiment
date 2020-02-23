package com.yevhenii

import java.time.format.DateTimeFormatter

import com.cybozu.labs.langdetect.DetectorFactory
import org.apache.spark.{SparkConf, SparkContext, mllib}
import org.apache.spark.sql.SparkSession

import scala.util.Properties
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.ml.classification.GBTClassifier
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, from_json, split}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.GradientBoostedTrees
import org.apache.spark.mllib.tree.configuration.BoostingStrategy
import org.apache.spark.mllib.tree.model.GradientBoostedTreesModel

import scala.util.Try
import org.apache.spark.ml.linalg
import org.apache.spark.sql.Row
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.ml.linalg.Vectors
import org.elasticsearch.spark.sql._
import org.elasticsearch.spark._
import org.elasticsearch.hadoop.cfg.ConfigurationOptions

import scala.reflect.macros.whitebox

object Application {
  val positiveLabelWord = "добре"
  val negativeFirstLabelWord = "погано"
  val negativeSecondLabelWord = "поганий"

  val hashingTF: HashingTF = new HashingTF(2000)

  def main(args: Array[String]): Unit ={
    Logger.getLogger("org").setLevel(Level.ERROR)

    val appName = "Application"
    val conf = new SparkConf()
    conf.setAppName(appName).setMaster("local")
    .set("spark.driver.allowMultipleContexts", "true")

    val model: GradientBoostedTreesModel = train(conf)

    val spark: SparkSession = SparkSession.builder.config(conf).getOrCreate();
    import spark.implicits._

    val tweetDF: DataFrame = spark.read.json("src/main/resources/dataset/dataset.json")

    tweetDF.

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
      .load()
      .select(from_json(col("value")
        .cast("string"), schema)
        .as("data"))
      .select("data.*")
      .map { row =>
        Message(
          row.getString(0),
          row.getString(1),
          row.getString(2),
          row.getString(3),
          row.getString(4),
          row.getString(5),
          row.getString(6),
          model.predict(toLabeled(row.getString(0)).features))
      }

    df.writeStream
      .format("console")
      .outputMode("append")
      .start()
      .awaitTermination()
  }

  def convertToVector(str: String): mllib.linalg.Vector = {
    return org.apache.spark.mllib.linalg.Vectors.dense(str.split(" ").map(s => s.toDouble))
  }
  def train(conf: SparkConf): GradientBoostedTreesModel ={

    val startTime = System.nanoTime()


    val sc = new SparkContext(conf)
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)

    val tweetDF: DataFrame = sqlContext.read.json("src/main/resources/dataset/dataset.json")

    val messages = tweetDF.select("message", "isPositive")
    println("Total messages: " + messages.count())

    val positiveMessages = messages.filter(messages("isPositive").contains(true))
    val countPositive = positiveMessages.count()

    val negativeMessages = messages.filter(messages("isPositive").contains(false))
    val countNegative = negativeMessages.count()

    println("Number of positive messages: " +  countPositive)
    println("Number of negative messages: " + countNegative)

    val smallestCommonCount = Math.min(countPositive, countNegative).toInt

    val messagesWorkingSet = positiveMessages.limit(smallestCommonCount).unionAll(negativeMessages.limit(smallestCommonCount))

    val messagesRDD = messagesWorkingSet.rdd
    //filter out tweets that can't be parsed
    val labeledTweets = getLabeledTweets(messagesRDD)

    //Map the input strings to a tuple of labeled point and input text
    val inputLabeled = labeledTweets.map(
      t => (t._1, hashingTF.transform(t._2)))
      .map(x => new LabeledPoint(x._1.toDouble, x._2))

    inputLabeled.take(5).foreach(println)

    val sampleSet = labeledTweets.take(1000).map(
      t => (t._1, hashingTF.transform(t._2), t._2))
      .map(x => (new LabeledPoint(x._1.toDouble, x._2), x._3))

    // separate the data into two sets (30% held out for validation testing)
    val splits = inputLabeled.randomSplit(Array(0.7, 0.3))
    val (trainingData, validationData) = (splits(0), splits(1))

    val boostingStrategy = BoostingStrategy.defaultParams("Classification")
    boostingStrategy.setNumIterations(30)
    boostingStrategy.treeStrategy.setNumClasses(2)
    boostingStrategy.treeStrategy.setMaxDepth(6)

    val model: GradientBoostedTreesModel = GradientBoostedTrees.train(trainingData, boostingStrategy)
    // evaluate model on test instances and compute test error
    val labelAndClassTrainingSet = trainingData.map { point =>
      val prediction = model.predict(point.features)
      Tuple2(point.label, prediction)
    }

    val labelAndClassValidationSet = validationData.map { point =>
      val prediction = model.predict(point.features)
      Tuple2(point.label, prediction)
    }

    val results = labelAndClassTrainingSet.collect()

    var positiveTotal = 0
    var positiveCorrect = 0
    var negativeTotal = 0
    var negativeCorrect = 0
    results.foreach(
      r => {
        if (r._1 == 1) {
          positiveTotal += 1
        } else if (r._1 == 0) {
          negativeTotal += 1
        }
        if (r._1 == 1 && r._2 ==1) {
          positiveCorrect += 1
        } else if (r._1 == 0 && r._2 == 0) {
          negativeCorrect += 1
        }
      }
    )

    //calculate test error
    val testErrorTrainingSet = labelAndClassTrainingSet.filter(r => r._1 != r._2).count.toDouble / trainingData.count()
    //pull up the results for validation set
    val validSetResults = labelAndClassValidationSet.collect()

    var positiveTotalValidSet = 0
    var negativeTotalValidSet = 0
    var positiveCorrectValidSet = 0
    var negativeCorrectValidSet = 0
    validSetResults.foreach(
      r => {
        if (r._1 == 1) {
          positiveTotalValidSet += 1
        } else if (r._1 == 0) {
          negativeTotalValidSet += 1
        }
        if (r._1 == 1 && r._2 ==1) {
          positiveCorrectValidSet += 1
        } else if (r._1 == 0 && r._2 == 0) {
          negativeCorrectValidSet += 1
        }
      }
    )

    val testErrorValidationSet = labelAndClassValidationSet.filter(r => r._1 != r._2).count.toDouble / validationData.count()

    val predictions = sampleSet.map {
      point =>
        val classifiedValue = model.predict(point._1.features)
        (point._1.label, classifiedValue, point._2)
    }

    //the first value is the real class label. 1 is positive, 0 is negative.
    //class is the second value
    predictions.take(100).foreach(x => println("label: " + x._1 + " class: " + x._2 + " text: " + x._3.mkString(" ")))

    val endTime = System.nanoTime()

    println("negative messages in Training Set: " + negativeTotal + " positive messages: " + positiveTotal)
    println("positive % correct: " + positiveCorrect.toDouble/positiveTotal)
    println("negative % correct: " + negativeCorrect.toDouble/negativeTotal)
    println("Test Error Training Set: " + testErrorTrainingSet)

    println("negative messages in Validation Set: " + negativeTotalValidSet + " positive messages: " + positiveTotalValidSet)
    println("positive % correct: " + positiveCorrectValidSet.toDouble/positiveTotalValidSet)
    println("negative % correct: " + negativeCorrectValidSet.toDouble/negativeTotalValidSet)
    println("Test Error Validation Set: " + testErrorValidationSet)

    println("Elapsed time: " + (endTime - startTime) / 1E9 + "secs")

    return model
  }

  def toLabeled(msg: String): LabeledPoint = {
    val messageSanitized = msg.toLowerCase().replaceAll(positiveLabelWord, "")
      .replaceAll(negativeFirstLabelWord, "")
      .replaceAll(negativeSecondLabelWord, "")

    val msgSeq = (1, messageSanitized.split(" ").toSeq)
    val t = (msgSeq._1, hashingTF.transform(msgSeq._2))

    return new LabeledPoint(msgSeq._1.toDouble, hashingTF.transform(msgSeq._2))
  }

  def getLabeledTweets(messagesRDD: RDD[Row]): RDD[(Int, Seq[String])] ={
    //filter out tweets that can't be parsed
    val positiveAndNegativeRecords = messagesRDD.map(
      row =>{
        Try{
          val msg = row(0).toString.toLowerCase()
          val isPositiveStatus = row(1).toString.toLowerCase()
          var isPositiveLabel = 0
          //filter by two negative words
          if(isPositiveStatus == "true"){
            isPositiveLabel = 1
          }else if(isPositiveStatus == "false"){
            isPositiveLabel = 0
          }
          val messageSanitized = msg.toLowerCase().replaceAll(positiveLabelWord, "")
            .replaceAll(negativeFirstLabelWord, "")
            .replaceAll(negativeSecondLabelWord, "")

          (isPositiveLabel, messageSanitized.split(" ").toSeq) //tuple returned
        }
      }
    )

    //filter out exceptions
    val exceptions = positiveAndNegativeRecords.filter(_.isFailure)
    println("Total records with exceptions: " + exceptions.count())
    exceptions.take(10).foreach(x => println(x.failed))

    val labeledTweets = positiveAndNegativeRecords.filter(_.isSuccess).map(_.get)
    println("Total records with successes: " + labeledTweets.count())

    labeledTweets.take(5).foreach(x => println(x))

    //return successfully parsed tweets
    labeledTweets
  }
}

case class Message(Id: String, Title: String, Body: String, Summary20: String, Cosine20: String,Summary40: String, Cosine40: String, sentiment: Double)