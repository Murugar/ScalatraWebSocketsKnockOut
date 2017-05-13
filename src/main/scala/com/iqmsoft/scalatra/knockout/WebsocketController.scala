package com.iqmsoft.scalatra.knockout

import java.util.concurrent.Executors

import org.json4s.JsonAST.{JNull, JField, JString, JObject}
import org.json4s.{JsonDSL, JValue, DefaultFormats, Formats}
import org.scalatra.SessionSupport
import org.scalatra._
import org.scalatra.json.{JacksonJsonSupport, JValueResult}
import org.slf4j.LoggerFactory
import org.scalatra.atmosphere._
import JsonDSL._
import scala.concurrent.{ExecutionContext, Future}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{Success, Failure}

class WebsocketController extends ScalatraServlet with JValueResult with JacksonJsonSupport with SessionSupport with AtmosphereSupport   {
  private final val log = LoggerFactory.getLogger(getClass)

  implicit protected val jsonFormats: Formats = DefaultFormats

  atmosphere("/") {
    new AtmosphereClient {
      private def uuidJson: JObject = "uid" -> uuid
      private var abortFutures = false
      private val rand = scala.util.Random

      private def writeToYou(jsonMessage: JValue): Unit = {
        log.info(s"YOU -> $jsonMessage")
        this.send(jsonMessage)
      }

      private def writeToAll(jsonMessage: JValue): Unit = {
        log.info(s"ALL -> $jsonMessage")
        this.broadcast(jsonMessage, Everyone)
      }

      private def writeToRest(jsonMessage: JValue): Unit = {
        log.info(s"REST -> $jsonMessage")
        this.broadcast(jsonMessage)
      }

      override def receive: AtmoReceive = {
        case message @ JsonMessage(JObject(JField("action", JString("getUID")) :: fields)) => {
          val json: JValue = message.content
          log.info(s"WS <- $json")
          this.writeToYou(uuidJson)
        }

        // add "trello" card
        case message @ JsonMessage(JObject(JField("action", JString("addCard")) :: fields)) => {
          val json: JValue = message.content
          log.info(s"WS <- $json")

          val cardJson: JValue = json findField {
            case JField("card", _) => true
            case _ => false
          }

          this.writeToAll(cardJson)
        }

        // futures
        case message @ JsonMessage(JObject(JField("action", JString("startFutures")) :: fields)) => {
          val json: JValue = message.content
          log.info(s"WS <- $json")

          // start the party in a future, to keep this thread free to accept requests
          Future {
            startFuturesParty()
          }

          this.writeToYou("futuresStarted" -> true)
        }

        case message @ JsonMessage(JObject(JField("action", JString("stopFutures")) :: fields)) => {
          val json: JValue = message.content
          log.info(s"WS <- $json")
          abortFutures = true
          this.writeToYou("futuresStarted" -> false)
        }

        // unknown
        case message @ JsonMessage(AnyRef) => {
          val json: JValue = message.content
          log.info(s"WS <- $json")
          throw new Exception("Unknown JSON command")
        }

        case Connected =>
          log.info("Client connected")

        case Disconnected(disconnector, Some(error)) =>
          log.info("Client disconnected ")

        case Error(Some(error)) =>
          // FIXME - what is the difference with the servlet-level "error" handler?
          error.printStackTrace()
      }

      private def startFuturesParty() = {
        abortFutures = false

        val numWorkers = sys.runtime.availableProcessors
        
        log.info(s"NUM OF WORKERS -> $numWorkers")
        
        
        val pool = Executors.newFixedThreadPool(numWorkers)
        implicit val ec = ExecutionContext.fromExecutorService(pool)

        /*
            Create futures equal to the size of the thread pool.
            Each future spawns a new one when it's done, to create
            back-pressure
         */
        for (threadIdx <- 1 to numWorkers) {
          inviteFutureToTheParty()
        }
      }

      private def inviteFutureToTheParty(): Future[JValue] = {
        val f: Future[JValue] = Future {
          // some futures will open "5.txt", which does not exist, to simulate error
          val fileNo = rand.nextInt(5) + 1
          val r = getClass.getResource(s"/future_file_shock/$fileNo.txt")
          val content: String = Source.fromURL(r).mkString
          "file" -> ("name" -> s"$fileNo.txt") ~ ("content" -> content)
        }

        f onComplete {
          case Success(json: JValue) => {
            this.writeToYou(json)
            if (!abortFutures) inviteFutureToTheParty()
          }
          case Failure(t) => {
            log.error(t.getClass.getName)
            this.writeToYou("file" -> ("error" -> t.getClass.getName))
            if (!abortFutures) inviteFutureToTheParty()
          }
        }

        f
      }
    }
  }
}
