package app.softnetwork.payment.scalatest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, Multipart, StatusCodes}
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.config.Settings.RootPath
import app.softnetwork.payment.config.Settings._
import app.softnetwork.payment.handlers.MockPaymentHandler
import app.softnetwork.payment.model.{BankAccountView, Card, KycDocument, KycDocumentValidationReport, PaymentAccountView, UboDeclarationView}
import app.softnetwork.payment.persistence.data.paymentKvDao
import app.softnetwork.payment.persistence.query.{GenericPaymentCommandProcessorStream, Scheduler2PaymentProcessorStream}
import app.softnetwork.payment.persistence.typed.MockPaymentBehavior
import app.softnetwork.payment.serialization.paymentFormats
import app.softnetwork.payment.service.MockPaymentService
import app.softnetwork.persistence.query.{EventProcessorStream, InMemoryJournalProvider}
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit
import app.softnetwork.persistence.typed.{EntityBehavior, Singleton}
import app.softnetwork.scheduler.handlers.SchedulerHandler
import app.softnetwork.scheduler.persistence.query.Entity2SchedulerProcessorStream
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior
import app.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior
import app.softnetwork.session.scalatest.{SessionServiceRoute, SessionTestKit}
import org.json4s.Formats
import org.scalatest.Suite

import java.nio.file.Paths

trait PaymentTestKit extends InMemoryPersistenceTestKit {_: Suite =>

  /**
    * initialize all behaviors
    *
    */
  override def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = _ => Seq(
    MockPaymentBehavior,
    SchedulerBehavior,
    SessionRefreshTokenBehavior
  )

  /**
    *
    * initialize all singletons
    */
  override def singletons: ActorSystem[_] => Seq[Singleton[_]] = _ => Seq(paymentKvDao)

  /**
    * initialize all event processor streams
    *
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys => Seq(
    new GenericPaymentCommandProcessorStream with MockPaymentHandler with InMemoryJournalProvider {
      override val forTests: Boolean = true
      override implicit def system: ActorSystem[_] = sys
    },
    new Scheduler2PaymentProcessorStream with MockPaymentHandler with InMemoryJournalProvider {
      override val tag: String = s"${MockPaymentBehavior.persistenceId}-scheduler"
      override protected val forTests: Boolean = true
      override implicit def system: ActorSystem[_] = sys
    },
    new Entity2SchedulerProcessorStream with SchedulerHandler with InMemoryJournalProvider {
      override protected val forTests: Boolean = true
      override implicit def system: ActorSystem[_] = sys
    }
  )

}

trait PaymentRouteTestKit extends SessionTestKit with PaymentTestKit { _: Suite =>
  import app.softnetwork.serialization._

  override implicit def formats: Formats = paymentFormats

  override def apiRoutes(system: ActorSystem[_]): Route =
    MockPaymentService(system).route ~
      SessionServiceRoute(system).route

  def loadPaymentAccount(): PaymentAccountView = {
    withCookies(
      Get(s"/$RootPath/$PaymentPath")
    ) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[PaymentAccountView]
    }
  }

  def loadCards(): Seq[Card] = {
    withCookies(
      Get(s"/$RootPath/$PaymentPath/$CardRoute")
    ) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Seq[Card]]
    }
  }

  def loadBankAccount(): BankAccountView = {
    withCookies(
      Get(s"/$RootPath/$PaymentPath/$BankRoute")
    ) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[BankAccountView]
    }
  }

  def addKycDocuments(): Unit = {
    val path = Paths.get(Thread.currentThread().getContextClassLoader.getResource("avatar.png").getPath)
    loadPaymentAccount().documents
      .filter(_.documentStatus == KycDocument.KycDocumentStatus.KYC_DOCUMENT_NOT_SPECIFIED)
      .map(_.documentType)
      .foreach { documentType =>
        withCookies(
          Post(
            s"/$RootPath/$PaymentPath/$KycRoute?documentType=$documentType",
            entity = Multipart.FormData.fromPath(
              "pages", ContentTypes.`application/octet-stream`, path, 100000
            ).toEntity
          ).withHeaders(
            RawHeader("Content-Type", "application/x-www-form-urlencoded"),
            RawHeader("Content-Type", "multipart/form-data")
          )
        ) ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          assert(loadKycDocumentStatus(documentType).status == KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATION_ASKED)
        }
      }
  }

  def loadKycDocumentStatus(documentType: KycDocument.KycDocumentType): KycDocumentValidationReport = {
    withCookies(
      Get(s"/$RootPath/$PaymentPath/$KycRoute?documentType=$documentType")
    ) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[KycDocumentValidationReport]
    }
  }

  def validateKycDocuments(): Unit = {
    loadPaymentAccount().documents
      .filter(_.documentStatus == KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATION_ASKED)
      .map(_.documentType)
      .foreach{ documentType =>
        withCookies(
          Get(s"/$RootPath/$PaymentPath/$KycRoute?documentType=$documentType")
        ) ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          val report = responseAs[KycDocumentValidationReport]
          assert(report.status == KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATION_ASKED)
          Get(s"/$RootPath/$PaymentPath/$HooksRoute?EventType=KYC_SUCCEEDED&RessourceId=${report.id}"
          ) ~> routes ~> check {
            status shouldEqual StatusCodes.OK
            assert(loadKycDocumentStatus(documentType).status == KycDocument.KycDocumentStatus.KYC_DOCUMENT_VALIDATED)
          }
        }
    }
  }

  def loadDeclaration(): UboDeclarationView = {
    withCookies(
      Get(s"/$RootPath/$PaymentPath/$DeclarationRoute")
    ) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[UboDeclarationView]
    }
  }
}