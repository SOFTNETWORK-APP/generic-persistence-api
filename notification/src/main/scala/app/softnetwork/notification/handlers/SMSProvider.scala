package app.softnetwork.notification.handlers

import _root_.akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.text.StringEscapeUtils
import app.softnetwork.notification.config.{SMSMode, Settings}
import org.softnetwork.notification.model.{NotificationStatus, NotificationStatusResult, NotificationAck, SMS}

import app.softnetwork.persistence._

/**
  * Created by smanciot on 14/04/2018.
  */
trait SMSProvider extends NotificationProvider[SMS] with StrictLogging {
  def send(notification: SMS)(implicit system: ActorSystem[_]): NotificationAck = throw new UnsupportedOperationException
}

trait MockSMSProvider extends SMSProvider with MockNotificationProvider[SMS]

trait SMSModeProvider extends SMSProvider {

  import java.io.{BufferedReader, InputStreamReader}
  import java.net.{HttpURLConnection, URL, URLEncoder}

  import java.util.Date

  import app.softnetwork.notification.config.SMSMode._
  import Status._

  import NotificationStatus._

  import scala.util.{Try, Success, Failure}

  lazy val config: Option[SMSMode.Config] = Settings.NotificationConfig.sms.mode

  override def send(notification: SMS)(implicit system: ActorSystem[_]): NotificationAck = {
    import notification._

    config match {
      case Some(conf) =>
        import conf._

        if(disabled){
          new NotificationAck(
            None,
            to.map(recipient => NotificationStatusResult(
              recipient,
              Undelivered,
              None
            )),
            now()
          )
        }
        else{
          val sendUrl = s"""
                           |$baseUrl/$version/sendSMS.do?
                           |accessToken=$accessToken
                           |&message=${URLEncoder.encode(StringEscapeUtils.unescapeHtml4(message).replaceAll("<br/>", "\\\n"), "ISO-8859-15")}
                           |&numero=${to.mkString(",")}
                           |&emetteur=${URLEncoder.encode(from.value, "ISO-8859-15")}
                           |${if(notificationUrl.isDefined)s"&notification_url=${notificationUrl.get}" else ""}
                           |${if(notificationUrlResponse.isDefined)s"&notification_ url_reponse=${notificationUrlResponse.get}" else ""}
                           |${if(stop) "&stop=2" else ""}
                           |""".stripMargin.replaceAll("\\s+", "")

          logger.info(sendUrl)

          val url = new URL(sendUrl)

          val connection = url.openConnection().asInstanceOf[HttpURLConnection]
          connection.setRequestMethod("GET") // POST if number of recipients is greater or equal to 300
          connection.setUseCaches(false)
          connection.setDoInput(true)
          //connection.setDoOutput(true)
          connection.getResponseCode match {

            case responseCode if responseCode == 200 || responseCode == 201 =>
              Try{
                val br = new BufferedReader(new InputStreamReader(connection.getInputStream))
                Stream.continually(br.readLine()).takeWhile(_ != null).mkString("")
              } match {
                case Success(responseData) =>
                  logger.info(responseData)
                  // code_retour | description | smsID
                  responseData.split("\\|").toList match {
                    case l if l.size == 3 =>
                      val smsId = l.last.trim
                      ResponseType(l.head.trim.toInt) match {
                        case ResponseType.ACCEPTED =>
                          new NotificationAck(
                            Some(smsId),
                            to.map(recipient => NotificationStatusResult(
                              recipient,
                              Pending,
                              None
                            )),
                            now()
                          )
                        case _                     =>
                          new NotificationAck(
                            Some(smsId),
                            to.map(recipient => NotificationStatusResult(
                              recipient,
                              Undelivered,
                              Some(l(1).trim)
                            )),
                            now()
                          )
                      }
                    case l if l.size == 2 =>
                      new NotificationAck(
                        None,
                        to.map(recipient => NotificationStatusResult(
                          recipient,
                          Undelivered,
                          Some(l.last.trim)
                        )),
                        now()
                      )
                    case _                =>
                      new NotificationAck(
                        None,
                        to.map(recipient => NotificationStatusResult(
                          recipient,
                          Undelivered,
                          None
                        )),
                        now()
                      )
                  }

                case Failure(f) =>
                  logger.error(f.getMessage, f)
                  new NotificationAck(
                    None,
                    to.map(recipient => NotificationStatusResult(
                      recipient,
                      Undelivered,
                      Some(f.getMessage)
                    )),
                    now()
                  )
              }

            case _ =>
              new NotificationAck(
                None,
                to.map(recipient => NotificationStatusResult(
                  recipient,
                  Undelivered,
                  None
                )),
                now()
              )
          }

        }

      case None =>
        new NotificationAck(
          None,
          to.map(recipient => NotificationStatusResult(
            recipient,
            Undelivered,
            None
          )),
          now()
        )
    }
  }

  override def ack(notification: SMS)(implicit system: ActorSystem[_]): NotificationAck = {
    val results = notification.results
    val uuid = notification.ackUuid.getOrElse("")
    config match {
      case Some(conf) =>
        import conf._
        val ackUrl = s"""
                         |$baseUrl/$version/compteRendu.do?
                         |accessToken=$accessToken
                         |&smsID=$uuid
                         |""".stripMargin.replaceAll("\\s+", "")
        logger.info(ackUrl)

        val url = new URL(ackUrl)

        val connection = url.openConnection().asInstanceOf[HttpURLConnection]
        connection.setRequestMethod("GET") // POST if number of recipients is greater or equal to 300
        connection.setUseCaches(false)
        connection.setDoInput(true)
        //connection.setDoOutput(true)
        connection.getResponseCode match {

          case responseCode if responseCode == 200 || responseCode == 201 =>
            Try{
              val br = new BufferedReader(new InputStreamReader(connection.getInputStream))
              Stream.continually(br.readLine()).takeWhile(_ != null).mkString("")
            } match {
              case Success(responseData) =>
                logger.info(responseData)
                // numéro_destinataire statut | numéro_destinataire statut | ...
                responseData.split("\\|").toList match {
                  case Nil =>
                    NotificationAck(Some(uuid), results, new Date())
                  case l   =>
                    NotificationAck(
                      Some(uuid),
                      l.map(i => {
                        val result = i.trim.split("\\s+").toList
                        var providerStatus: Option[String] = None
                        val status =
                          Try(Status(result.last.toInt)) match {
                            case Success(s) =>
                              providerStatus = Some(s.toString)
                              s match {
                                case SENT                 => Sent
                                case DELIVERED            => Delivered
                                case READ                 => Delivered
                                case RECEIVED             => Delivered
                                case UNREAD               => Delivered
                                case REJECTED             => Rejected
                                case INSUFFICIENT_CREDITS => Undelivered
                                case INTERNAL_ERROR       => Undelivered
                                case NOT_DELIVERABLE      => Undelivered
                                case ROUTING_ERROR        => Undelivered
                                case RECEIPT_ERROR        => Undelivered
                                case MESSAGE_ERROR        => Undelivered
                                case TOO_LONG_MESSAGE     => Undelivered
                                case _                    => Pending
                              }
                            case Failure(_) => Pending
                          }
                        val error = status match {
                          case Rejected    => providerStatus
                          case Undelivered => providerStatus
                          case _           => None
                        }
                        NotificationStatusResult(
                          if (result.size == 2)
                            result.head
                          else
                            notification.to.head,
                          status,
                          error
                        )
                      }),
                      new Date()
                    )
                }

              case Failure(f) =>
                logger.error(f.getMessage, f)
                NotificationAck(Some(uuid), results, new Date())
            }

          case _ => NotificationAck(Some(uuid), results, new Date())
        }

      case None => NotificationAck(Some(uuid), results, new Date())
    }
  }
}

object SMSModeProvider extends SMSModeProvider

object MockSMSProvider extends MockSMSProvider
