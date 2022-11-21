package app.softnetwork.resource.handlers

import akka.actor.typed.ActorSystem

import java.io.{ByteArrayInputStream, File}
import app.softnetwork.resource.message.ResourceMessages._
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.utils.HashTools
import app.softnetwork.resource.config.Settings.{BaseUrl, ImageSizes, ResourcePath}
import app.softnetwork.resource.message.ResourceEvents._
import app.softnetwork.resource.scalatest.ResourceToLocalFileSystemTestKit
import app.softnetwork.resource.spi.{LocalFileSystemProvider, SizeOption}
import app.softnetwork.resource.utils.ResourceTools

import java.nio.file.{Files, Paths}
import scala.concurrent.Future
import scala.reflect.io.Directory

/**
  * Created by smanciot on 27/04/2020.
  */
class ResourceHandlerSpec extends ResourceHandler with LocalFileSystemProvider with AnyWordSpecLike
  with ResourceToLocalFileSystemTestKit {

  implicit lazy val system: ActorSystem[_] = typedSystem()

  var _bytes: Array[Byte] = _

  var _md5: String = _

  val uri: Option[String] = Some("/resources")

  override def beforeAll(): Unit = {
    super.beforeAll()
    val path = Paths.get(Thread.currentThread().getContextClassLoader.getResource("avatar.png").getPath)
    _bytes = Files.readAllBytes(path)
    _md5 = HashTools.hashStream(
      new ByteArrayInputStream(_bytes)
    ).getOrElse("")
    val dir = new Directory(new File(rootDir))
    dir.deleteRecursively()
  }

  "Resource handler" must {

    "create resource" in {
      val probe = createTestProbe[ResourceEvent]()
      subscribeProbe(probe)
      createOrUpdateResource("create") await {
        case ResourceCreated =>
          probe.expectMessageType[ResourceCreatedEvent]
          assert(Files.exists(Paths.get(s"$rootDir${uri.getOrElse("")}/create")))
        case _ => fail()
      }
    }

    "update resource" in {
      val probe = createTestProbe[ResourceEvent]()
      subscribeProbe(probe)
      createOrUpdateResource("update", update = true) await {
        case ResourceUpdated =>
          probe.expectMessageType[ResourceUpdatedEvent]
          assert(Files.exists(Paths.get(s"$rootDir${uri.getOrElse("")}/update")))
        case _ => fail()
      }
    }

    "load resource" in {
      val probe = createTestProbe[ResourceEvent]()
      subscribeProbe(probe)
      createOrUpdateResource("load") await {
        case ResourceCreated =>
          probe.expectMessageType[ResourceCreatedEvent]
          assert(Files.exists(Paths.get(s"$rootDir${uri.getOrElse("")}/load")))
          ? ("load", LoadResource("load")) await {
            case r: ResourceLoaded =>
              r.resource.md5 shouldBe _md5
              for(size <- ImageSizes.values){
                loadResource("load", uri, None, Seq(SizeOption(size)): _*) match {
                  case Some(_) =>
                  case _ => fail()
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
          assert(Files.exists(Paths.get(s"$rootDir${uri.getOrElse("")}/delete")))
          ? ("delete", DeleteResource("delete")) await {
            case ResourceDeleted =>
              probe.expectMessageType[ResourceDeletedEvent]
              assert(!Files.exists(Paths.get(s"$rootDir${uri.getOrElse("")}/delete")))
            case _ => fail()
          }
        case _ => fail()
      }
    }

    "list resources" in {
      val resources = listResources(uri.getOrElse("/"))
      assert(resources.nonEmpty)
      assert(resources.forall(!_.directory))
      val files = resources.map(_.name)
      assert(files.contains("create"))
      assert(files.contains("update"))
      assert(files.contains("load"))
      assert(!files.contains("delete"))
    }
  }

  "Resource tools" must {
    "compute resource uri" in {
      assert(ResourceTools.resourceUri("first", "second", "third#forth") == s"$BaseUrl/$ResourcePath/first/second/third%23forth")
    }
  }
  private[this] def createOrUpdateResource(entityId: String, update: Boolean = false): Future[ResourceResult] = {
    ? (entityId,
      if(update){
        UpdateResource(entityId, _bytes, uri)
      }
      else {
        CreateResource(entityId, _bytes, uri)
      }
    )
  }
}
