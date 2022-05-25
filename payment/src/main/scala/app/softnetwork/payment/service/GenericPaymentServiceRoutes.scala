package app.softnetwork.payment.service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Route
import app.softnetwork.api.server.ApiRoutes
import app.softnetwork.payment.serialization.paymentFormats
import org.json4s.Formats

trait GenericPaymentServiceRoutes extends ApiRoutes{
  override implicit def formats: Formats = paymentFormats
}

trait PaymentServiceRoutes extends GenericPaymentServiceRoutes {
  override def apiRoutes(system: ActorSystem[_]): Route = MangoPayPaymentService(system).route
}

trait MockPaymentServiceRoutes extends GenericPaymentServiceRoutes {
  override def apiRoutes(system: ActorSystem[_]): Route = MockPaymentService(system).route
}