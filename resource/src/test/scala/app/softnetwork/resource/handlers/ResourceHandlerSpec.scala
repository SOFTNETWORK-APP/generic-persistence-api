package app.softnetwork.resource.handlers

import akka.actor.typed.ActorSystem

import java.io.{ByteArrayInputStream, File}
import app.softnetwork.resource.message.ResourceMessages._
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.utils.HashTools
import app.softnetwork.resource.ResourceTestKit
import app.softnetwork.resource.config.Settings.ImageSizes
import app.softnetwork.resource.message.ResourceEvents._
import app.softnetwork.resource.persistence.query.LocalFileSystemResourceProvider
import app.softnetwork.resource.spi.SizeOption

import java.nio.file.{Files, Paths}
import scala.concurrent.Future

/**
  * Created by smanciot on 27/04/2020.
  */
class ResourceHandlerSpec extends ResourceHandler with LocalFileSystemResourceProvider with AnyWordSpecLike with ResourceTestKit {

  var _bytes: Array[Byte] = _

  var _md5: String = _

  implicit lazy val system: ActorSystem[_] = typedSystem()

  override def beforeAll(): Unit = {
    super.beforeAll()
    val path = Paths.get(Thread.currentThread().getContextClassLoader.getResource("avatar.png").getPath)
    _bytes = Files.readAllBytes(path)
    _md5 = HashTools.hashStream(
      new ByteArrayInputStream(_bytes)
    ).getOrElse("")
    new File(rootDir).delete()
  }

  "Resource handler" must {

    "create resource" in {
      val probe = createTestProbe[ResourceEvent]()
      subscribeProbe(probe)
      createOrUpdateResource("create") await {
        case ResourceCreated =>
          probe.expectMessageType[ResourceCreatedEvent]
          assert(Files.exists(Paths.get((s"$rootDir/create"))))
        case _ => fail()
      }
    }

    "update resource" in {
      val probe = createTestProbe[ResourceEvent]()
      subscribeProbe(probe)
      createOrUpdateResource("update", update = true) await {
        case ResourceUpdated =>
          probe.expectMessageType[ResourceUpdatedEvent]
          assert(Files.exists(Paths.get((s"$rootDir/update"))))
        case _ => fail()
      }
    }

    "load resource" in {
      val probe = createTestProbe[ResourceEvent]()
      subscribeProbe(probe)
      createOrUpdateResource("load") await {
        case ResourceCreated =>
          probe.expectMessageType[ResourceCreatedEvent]
          assert(Files.exists(Paths.get((s"$rootDir/load"))))
          ? ("load", LoadResource("load")) await {
            case r: ResourceLoaded =>
              r.resource.md5 shouldBe _md5
              for(size <- ImageSizes.values){
                loadResource("load", None, Seq(SizeOption(size)): _*) match {
                  case Some(_) =>
                  case _ => false
                }
              }
            case other => fail(other)
          }
        case other => fail(other)
      }
    }

    "delete resource" in {
      val probe = createTestProbe[ResourceEvent]()
      subscribeProbe(probe)
      createOrUpdateResource("delete") await {
        case ResourceCreated =>
          probe.expectMessageType[ResourceCreatedEvent]
          assert(Files.exists(Paths.get((s"$rootDir/delete"))))
          ? ("delete", DeleteResource("delete")) await {
            case ResourceDeleted =>
              probe.expectMessageType[ResourceDeletedEvent]
              assert(!Files.exists(Paths.get((s"$rootDir/delete"))))
            case _ => fail()
          }
        case _ => fail()
      }
    }

  }

  private[this] def createOrUpdateResource(entityId: String, update: Boolean = false): Future[ResourceResult] = {
    ? (entityId,
      if(update){
        UpdateResource(entityId, _bytes)
      }
      else {
        CreateResource(entityId, _bytes)
      }
    )
  }
}
