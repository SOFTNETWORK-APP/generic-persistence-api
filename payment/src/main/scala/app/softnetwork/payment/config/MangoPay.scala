package app.softnetwork.payment.config

import Settings._
import com.mangopay.MangoPayApi
import com.mangopay.core.enumerations.{EventType, HookStatus}
import com.mangopay.entities.Hook

import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 16/08/2018.
  */
object MangoPay extends StrictLogging{

  var hooksInitialized = false

  case class Config(
                     clientId: String,
                     apiKey: String,
                     baseUrl: String,
                     version: String,
                     debug: Boolean,
                     technicalErrors: Set[String],
                     secureModePath: String,
                     hooksPath: String,
                     mandatePath: String){

    lazy val secureModeReturnUrl = s"""$BaseUrl/$secureModePath/$SecureModeRoute"""

    lazy val preAuthorizeCardFor3DS = s"$secureModeReturnUrl/$PreAuthorizeCardRoute"

    lazy val payInFor3DS = s"$secureModeReturnUrl/$PayInRoute"

    lazy val recurringPaymentFor3DS = s"$secureModeReturnUrl/$RecurringPaymentRoute"

    lazy val hooksBaseUrl = s"""$BaseUrl/$hooksPath/$HooksRoute"""

    lazy val mandateReturnUrl = s"""$BaseUrl/$mandatePath/$MandateRoute"""
  }

  def apply(): MangoPayApi = {
    import Settings.MangoPayConfig._
    val mangoPayApi = new MangoPayApi
    mangoPayApi.getConfig.setBaseUrl(baseUrl)
    mangoPayApi.getConfig.setClientId(clientId)
    mangoPayApi.getConfig.setClientPassword(apiKey)
    mangoPayApi.getConfig.setDebugMode(debug)
    mangoPayApi
  }

  def createHooks(): Unit = {
    if(!hooksInitialized){
      import scala.collection.JavaConverters._
      val hooks: List[Hook] =
        Try(MangoPay().getHookApi.getAll) match {
          case Success(s) => s.asScala.toList
          case Failure(f) =>
            logger.error(f.getMessage, f.getCause)
            List.empty
        }
      createOrUpdateHook(EventType.KYC_SUCCEEDED, hooks)
      createOrUpdateHook(EventType.KYC_FAILED, hooks)
      createOrUpdateHook(EventType.KYC_OUTDATED, hooks)
      createOrUpdateHook(EventType.TRANSFER_NORMAL_SUCCEEDED, hooks)
      createOrUpdateHook(EventType.TRANSFER_NORMAL_FAILED, hooks)
      createOrUpdateHook(EventType.UBO_DECLARATION_REFUSED, hooks)
      createOrUpdateHook(EventType.UBO_DECLARATION_VALIDATED, hooks)
      createOrUpdateHook(EventType.UBO_DECLARATION_INCOMPLETE, hooks)
      createOrUpdateHook(EventType.USER_KYC_REGULAR, hooks)
      createOrUpdateHook(EventType.MANDATE_FAILED, hooks)
      createOrUpdateHook(EventType.MANDATE_SUBMITTED, hooks)
      createOrUpdateHook(EventType.MANDATE_CREATED, hooks)
      createOrUpdateHook(EventType.MANDATE_ACTIVATED, hooks)
      createOrUpdateHook(EventType.MANDATE_EXPIRED, hooks)
      hooksInitialized = true
    }
  }

  def createOrUpdateHook(eventType: EventType, hooks: List[Hook]): Unit = {
    import Settings.MangoPayConfig._
    Try {
      hooks.find(_.getEventType == eventType) match {
        case Some(previousHook) =>
          previousHook.setStatus(HookStatus.ENABLED)
          previousHook.setUrl(s"$hooksBaseUrl")
          logger.info(s"Updating Mangopay Hook ${previousHook.getId}")
          MangoPay().getHookApi.update(previousHook)
        case _ =>
          val hook = new Hook()
          hook.setEventType(eventType)
          hook.setStatus(HookStatus.ENABLED)
          hook.setUrl(s"$hooksBaseUrl")
          MangoPay().getHookApi.create(hook)
      }
    } match {
      case Success(_) =>
      case Failure(f) =>
        logger.error(s"$eventType -> ${f.getMessage}", f.getCause)
    }
  }
}
