package app.softnetwork.elastic.client

import java.io.{ByteArrayInputStream, FileOutputStream, File}
import java.util.concurrent.TimeUnit
import java.util.{Date, UUID}

import akka.actor.ActorSystem
import com.fasterxml.jackson.core.JsonParseException
import com.sksamuel.elastic4s.searches.queries.matches.MatchAllQuery
import io.searchbox.client.JestClient
import io.searchbox.indices.CreateIndex
import io.searchbox.indices.aliases.AliasExists
import io.searchbox.indices.mapping.PutMapping
import io.searchbox.indices.settings.GetSettings

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import app.softnetwork.persistence._
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.serialization._
import app.softnetwork.elastic.model.Sample
import app.softnetwork.elastic.scalatest.ElasticDockerTestKit
import app.softnetwork.elastic.utils._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by smanciot on 28/06/2018.
  */
class ElasticClientSpec extends AnyFlatSpecLike with ElasticDockerTestKit with Matchers {

  implicit val system = ActorSystem(generateUUID())

  implicit val executionContext = system.dispatcher

  implicit val formats = commonFormats

  lazy val esCredentials = ElasticCredentials(elasticURL, "", "")

  lazy val pClient = new PersonApi(esCredentials)
  lazy val sClient = new SampleApi(esCredentials)
  lazy val bClient = new BinaryApi(esCredentials)

  override def beforeAll(): Unit = {
    super.beforeAll()
    pClient.createIndex("person")
  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), Duration(30, TimeUnit.SECONDS))
    super.afterAll()
  }

  "Creating an index and then delete it" should "work fine" in {
    pClient.createIndex("create_delete")
    blockUntilIndexExists("create_delete")
    "create_delete" should beCreated

    pClient.deleteIndex("create_delete")
    blockUntilIndexNotExists("create_delete")
    "create_delete" should not(beCreated())
  }

  "Adding an alias" should "work" in {
    pClient.addAlias("person", "person_alias")

    val aliasExists = new AliasExists.Builder().build()
    pClient.jestClient.execute(aliasExists).isSucceeded shouldBe true
  }

  private def settings =
    pClient.jestClient
      .execute(new GetSettings.Builder().addIndex("person").build())
      .getJsonObject
      .getAsJsonObject("person")
      .getAsJsonObject("settings")

  "Toggle refresh" should "work" in {
    pClient.toggleRefresh("person", enable = false)

    settings.getAsJsonObject("index").get("refresh_interval").getAsString shouldBe "-1"

    pClient.toggleRefresh("person", enable = true)
    settings.getAsJsonObject("index").get("refresh_interval").getAsString shouldBe "1s"
  }

  "Updating number of replicas" should "work" in {
    pClient.setReplicas("person", 3)
    settings.getAsJsonObject("index").get("number_of_replicas").getAsString shouldBe "3"

    pClient.setReplicas("person", 0)
    settings.getAsJsonObject("index").get("number_of_replicas").getAsString shouldBe "0"
  }

  val persons = List(
    """ { "uuid": "A12", "name": "Homer Simpson", "birthDate": "1967-11-21 12:00:00"} """,
    """ { "uuid": "A14", "name": "Moe Szyslak",   "birthDate": "1967-11-21 12:00:00"} """,
    """ { "uuid": "A16", "name": "Barney Gumble", "birthDate": "1969-05-09 21:00:00"} """
  )

  private val personsWithUpsert = persons :+ """ { "uuid": "A16", "name": "Barney Gumble2", "birthDate": "1969-05-09 21:00:00"} """

  val children = List(
    """ { "parentId": "A16", "name": "Steve Gumble", "birthDate": "1999-05-09 21:00:00"} """,
    """ { "parentId": "A16", "name": "Josh Gumble", "birthDate": "1999-05-09 21:00:00"} """
  )

  "Bulk index valid json without id key and suffix key" should "work" in {
    implicit val bulkOptions = BulkOptions("person1", "person", 2)
    implicit val jclient = pClient.jestClient
    val indices = pClient.bulk[String](persons.iterator, identity, None, None, None)

    indices should contain only "person1"

    blockUntilCount(3, "person1")

    "person1" should haveCount(3)

    val response = client.execute {
      search("person1").query(MatchAllQuery())
    } complete()

    response.result.hits.hits.foreach { h =>
      h.id should not be h.sourceField("uuid")
    }

    response.result.hits.hits
      .map(_.sourceField("name")) should contain allOf ("Homer Simpson", "Moe Szyslak", "Barney Gumble")
  }

  "Bulk index valid json with an id key but no suffix key" should "work" in {
    pClient.jestClient.execute(new CreateIndex.Builder("person2").build())
    val childMapping = new PutMapping.Builder(
      "person2",
      "child",
      "{ \"child\" : { \"_parent\" : {\"type\": \"person\"}, \"properties\" : { \"name\" : {\"type\" : \"string\", \"index\" : \"not_analyzed\"} } } }"
    ).build()
    pClient.jestClient.execute(childMapping)

    implicit val bulkOptions = BulkOptions("person2", "person", 1000)
    implicit val jclient = pClient.jestClient
    val indices = pClient.bulk[String](persons.iterator, identity, Some("uuid"), None, None)
    refresh(indices)

    indices should contain only "person2"

    blockUntilCount(3, "person2")

    "person2" should haveCount(3)

    val response = client.execute {
      search("person2").query(MatchAllQuery())
    } complete()

    response.result.hits.hits.foreach { h =>
      h.id shouldBe h.sourceField("uuid")
    }

    response.result.hits.hits
      .map(_.sourceField("name")) should contain allOf ("Homer Simpson", "Moe Szyslak", "Barney Gumble")

    // FIXME elastic >= v 6.x no more multiple Parent / Child relationship allowed within the same index
//    val childIndices =
//      pClient.bulk[String](children.iterator, identity, None, None, None, None, None, Some("parentId"))(
//        jclient,
//        BulkOptions("person2", "child", 1000),
//        system)
//    pClient.refresh("person2")
//
//    childIndices should contain only "person2"
//
//    blockUntilCount(2, "person2", "child")
//
//    "person2" should haveCount(5)
  }

  "Bulk index valid json with an id key and a suffix key" should "work" in {
    implicit val bulkOptions = BulkOptions("person", "person", 1000)
    implicit val jclient = pClient.jestClient
    val indices = pClient.bulk[String](persons.iterator, identity, Some("uuid"), Some("birthDate"), None, None)
    refresh(indices)

    indices should contain allOf ("person-1967-11-21", "person-1969-05-09")

    blockUntilCount(2, "person-1967-11-21")
    blockUntilCount(1, "person-1969-05-09")

    "person-1967-11-21" should haveCount(2)
    "person-1969-05-09" should haveCount(1)

    val response = client.execute {
      search("person-1967-11-21", "person-1969-05-09").query(MatchAllQuery())
    } complete()  

    response.result.hits.hits.foreach { h =>
      h.id shouldBe h.sourceField("uuid")
    }

    response.result.hits.hits
      .map(_.sourceField("name")) should contain allOf ("Homer Simpson", "Moe Szyslak", "Barney Gumble")
  }

  "Bulk index invalid json with an id key and a suffix key" should "work" in {
    implicit val bulkOptions = BulkOptions("person_error", "person", 1000)
    implicit val jclient = pClient.jestClient
    intercept[JsonParseException] {
      val invalidJson = persons :+ "fail"
      pClient.bulk[String](invalidJson.iterator, identity, None, None, None)
    }
  }

  "Bulk upsert valid json with an id key but no suffix key" should "work" in {
    implicit val bulkOptions = BulkOptions("person4", "person", 1000)
    implicit val jclient = pClient.jestClient
    val indices =
      pClient.bulk[String](personsWithUpsert.iterator, identity, Some("uuid"), None, None, Some(true))
    refresh(indices)

    indices should contain only "person4"

    blockUntilCount(3, "person4")

    "person4" should haveCount(3)

    val response = client.execute {
      search("person4").query(MatchAllQuery())
    } complete()  

    response.result.hits.hits.foreach { h =>
      h.id shouldBe h.sourceField("uuid")
    }

    response.result.hits.hits
      .map(_.sourceField("name")) should contain allOf ("Homer Simpson", "Moe Szyslak", "Barney Gumble2")
  }

  "Bulk upsert valid json with an id key and a suffix key" should "work" in {
    implicit val bulkOptions = BulkOptions("person5", "person", 1000)
    implicit val jclient = pClient.jestClient
    val indices = pClient.bulk[String](personsWithUpsert.iterator, identity, Some("uuid"), Some("birthDate"), None, Some(true))
    refresh(indices)

    indices should contain allOf ("person5-1967-11-21", "person5-1969-05-09")

    blockUntilCount(2, "person5-1967-11-21")
    blockUntilCount(1, "person5-1969-05-09")

    "person5-1967-11-21" should haveCount(2)
    "person5-1969-05-09" should haveCount(1)

    val response = client.execute {
      search("person5-1967-11-21", "person5-1969-05-09").query(MatchAllQuery())
    } complete()  

    response.result.hits.hits.foreach { h =>
      h.id shouldBe h.sourceField("uuid")
    }

    response.result.hits.hits
      .map(_.sourceField("name")) should contain allOf ("Homer Simpson", "Moe Szyslak", "Barney Gumble2")
  }

  "Count" should "work" in {
    implicit val bulkOptions = BulkOptions("person6", "person", 1000)
    implicit val jclient = pClient.jestClient
    val indices =
      pClient.bulk[String](personsWithUpsert.iterator, identity, Some("uuid"), None, None, Some(true))
    refresh(indices)

    indices should contain only "person6"

    blockUntilCount(3, "person6")

    "person6" should haveCount(3)

    import scala.collection.immutable.Seq

    val response = pClient.countAsync("{}", Seq[String]("person6"), Seq[String]()) complete()  

    response.getCount should ===(3)
  }

  "Search" should "work" in {
    implicit val bulkOptions = BulkOptions("person7", "person", 1000)
    implicit val jclient = pClient.jestClient
    val indices =
      pClient.bulk[String](personsWithUpsert.iterator, identity, Some("uuid"), None, None, Some(true))
    refresh(indices)

    indices should contain only "person7"

    blockUntilCount(3, "person7")

    "person7" should haveCount(3)

    pClient.searchAsync("select * from person7") assert {
      case Some(s) => s.getTotal should ===(3)
      case _ => fail()
    }

    pClient.searchAsync("select * from person7 where _id=\"A16\"") assert {
      case Some(s) => s.getTotal should ===(1)
      case _ => fail()
    }

  }

  "Get all" should "work" in {
    implicit val bulkOptions = BulkOptions("person8", "person", 1000)
    implicit val jclient = pClient.jestClient
    val indices =
      pClient.bulk[String](personsWithUpsert.iterator, identity, Some("uuid"), None, None, Some(true))
    refresh(indices)

    indices should contain only "person8"

    blockUntilCount(3, "person8")

    "person8" should haveCount(3)

    val response = pClient.getAll[Person]("select * from person8")

    response.size should ===(3)

  }

  "Get" should "work" in {
    implicit val bulkOptions = BulkOptions("person9", "person", 1000)
    implicit val jclient = pClient.jestClient
    val indices =
      pClient.bulk[String](personsWithUpsert.iterator, identity, Some("uuid"), None, None, Some(true))
    refresh(indices)

    indices should contain only "person9"

    blockUntilCount(3, "person9")

    "person9" should haveCount(3)

    val response = pClient.get[Person]("A16", Some("person9"))

    response.isDefined shouldBe true
    response.get.uuid shouldBe "A16"

  }

  "Index" should "work" in {
    implicit val jclient = sClient.jestClient
    val uuid = UUID.randomUUID().toString
    val sample = Sample(uuid)
    val result = sClient.index(sample)
    result shouldBe uuid

    val result2 = sClient.get[Sample](uuid)
    result2.isDefined shouldBe true
    result2.get.uuid shouldBe uuid
  }

  "Update" should "work" in {
    implicit val jclient = sClient.jestClient
    val uuid = UUID.randomUUID().toString
    val sample = Sample(uuid)
    val result = sClient.update(sample)
    result shouldBe uuid

    val result2 = sClient.get[Sample](uuid)
    result2.isDefined shouldBe true
    result2.get.uuid shouldBe uuid
  }

  "Delete" should "work" in {
    implicit val jclient = sClient.jestClient
    val uuid = UUID.randomUUID().toString
    val sample = Sample(uuid)
    val result = sClient.index(sample)
    result shouldBe uuid

    val result2 = sClient.delete(sample.uuid, Some("sample"), Some("sample"))
    result2 shouldBe true

    val result3 = sClient.get(uuid)
    result3.isEmpty shouldBe true
  }

  "Index binary data" should "work" in {
    implicit val jclient = bClient.jestClient
    bClient.createIndex("binaries") shouldBe true
    val mapping =
      """{
        |  "test": {
        |    "properties": {
        |      "uuid": {
        |        "type": "keyword",
        |        "index": true
        |      },
        |      "createdDate": {
        |        "type": "date"
        |      },
        |      "lastUpdated": {
        |        "type": "date"
        |      },
        |      "content": {
        |        "type": "binary"
        |      },
        |      "md5": {
        |        "type": "keyword"
        |      }
        |    }
        |  }
        |}
      """.stripMargin
    bClient.setMapping("binaries", "test", mapping) shouldBe true
    for(uuid <- Seq("png", "jpg", "pdf")){
      val file = new File(Thread.currentThread().getContextClassLoader.getResource(s"avatar.$uuid").getPath)
      import ImageTools._
      import HashTools._
      import Base64Tools._
      val encoded = encodeImageBase64(file).getOrElse("")
      val binary = Binary(uuid, content=encoded, md5 = hashStream(new ByteArrayInputStream(decodeBase64(encoded))).getOrElse(""))
      bClient.index(binary) shouldBe uuid
      bClient.get[Binary](uuid) match {
        case Some(result) =>
          val decoded = decodeBase64(result.content)
          val out = new File("/tmp", file.getName)
          val fis = new FileOutputStream(out)
          fis.write(decoded)
          fis.close()
          hashFile(out).getOrElse("") shouldBe binary.md5
        case _            => fail("no result found for \""+uuid+"\"")
      }
    }
  }
}

case class Person(uuid: String, name: String, birthDate: String, var createdDate: Date = now(), var lastUpdated: Date = now())
extends Timestamped

case class Binary(uuid: String, var createdDate: Date = now(), var lastUpdated: Date = now(), val content: String, val md5: String)
  extends Timestamped

class PersonApi(ec: ElasticCredentials) extends ElasticApi[Person] with ManifestWrapper[Person]{
  override protected val manifestWrapper: ManifestW = ManifestW()
  override protected def credentials: Option[ElasticCredentials] = Some(ec)
  implicit lazy val jestClient: JestClient = apply(ec, multithreaded = false)
}

class SampleApi(ec: ElasticCredentials) extends ElasticApi[Sample] with ManifestWrapper[Sample]{
  override protected val manifestWrapper: ManifestW = ManifestW()
  override protected def credentials: Option[ElasticCredentials] = Some(ec)
  implicit lazy val jestClient: JestClient = apply(ec, multithreaded = false)
}

class BinaryApi(ec: ElasticCredentials) extends ElasticApi[Binary] with ManifestWrapper[Binary]{
  override protected val manifestWrapper: ManifestW = ManifestW()
  override protected def credentials: Option[ElasticCredentials] = Some(ec)
  implicit lazy val jestClient: JestClient = apply(ec, multithreaded = false)
}
