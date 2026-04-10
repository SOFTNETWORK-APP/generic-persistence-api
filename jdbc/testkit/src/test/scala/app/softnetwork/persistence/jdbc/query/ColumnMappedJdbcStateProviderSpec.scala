package app.softnetwork.persistence.jdbc.query

import app.softnetwork.persistence.jdbc.scalatest.{H2TestKit, JdbcPersistenceTestKit}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import slick.jdbc.H2Profile

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext

class ColumnMappedJdbcStateProviderSpec
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with TestEntityProvider
    with H2Profile
    with H2TestKit {

  override implicit def executionContext: ExecutionContext = classicSystem.dispatcher

  override def beforeAll(): Unit = {
    super.beforeAll()
    initTable()
  }

  private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
  private val alice =
    TestEntity("id-1", now, now, "Alice", "alice@acme.com", "active")
  private val bob =
    TestEntity("id-2", now, now, "Bob", "bob@acme.com", "active")
  private val charlie =
    TestEntity("id-3", now, now, "Charlie", "charlie@acme.com", "suspended")

  "initTable" should "run Flyway migration and be idempotent" in {
    noException should be thrownBy initTable()
  }

  "createDocument" should "insert a row with typed columns" in {
    createDocument(alice) shouldBe true
    createDocument(bob) shouldBe true
    createDocument(charlie) shouldBe true
  }

  "loadDocument" should "retrieve and deserialize via fromRow" in {
    loadDocument("id-1") shouldBe Some(alice)
  }

  it should "return None for non-existent uuid" in {
    loadDocument("non-existent") shouldBe None
  }

  "updateDocument" should "modify typed columns" in {
    val updated = alice.copy(name = "Alice Updated", lastUpdated = Instant.now())
    updateDocument(updated) shouldBe true
    loadDocument("id-1").map(_.name) shouldBe Some("Alice Updated")
  }

  it should "upsert when entity does not exist" in {
    val newEntity = TestEntity("id-4", now, now, "Diana", "diana@acme.com", "active")
    updateDocument(newEntity, upsert = true) shouldBe true
    loadDocument("id-4").map(_.name) shouldBe Some("Diana")
  }

  "searchDocuments" should "return entities matching SQL WHERE on typed columns" in {
    val results = searchDocuments("status = 'active'")
    results.map(_.uuid) should contain theSameElementsAs List("id-1", "id-2", "id-4")
  }

  it should "support indexed column lookup" in {
    val results = searchDocuments("email = 'bob@acme.com'")
    results.map(_.uuid) shouldBe List("id-2")
  }

  it should "support compound WHERE clauses" in {
    val results = searchDocuments("status = 'active' AND name LIKE 'B%'")
    results.map(_.name) shouldBe List("Bob")
  }

  it should "return empty list for no matches" in {
    searchDocuments("status = 'nonexistent'") shouldBe Nil
  }

  "findByEmail (type-safe Slick finder)" should "return the correct entity" in {
    findByEmail("bob@acme.com").map(_.uuid) shouldBe Some("id-2")
  }

  it should "return None for unknown email" in {
    findByEmail("unknown@acme.com") shouldBe None
  }

  "findByStatus (type-safe Slick finder)" should "return matching entities" in {
    findByStatus("suspended").map(_.uuid) shouldBe List("id-3")
  }

  "deleteDocument" should "soft-delete (loadDocument returns None)" in {
    deleteDocument("id-3") shouldBe true
    loadDocument("id-3") shouldBe None
  }

  it should "return true for already-absent entity (idempotent)" in {
    deleteDocument("id-3") shouldBe true
  }

  it should "return true for non-existent uuid (idempotent)" in {
    deleteDocument("never-existed") shouldBe true
  }

  "findByEmail" should "return None for soft-deleted entities" in {
    findByEmail("charlie@acme.com") shouldBe None
  }

  "destroy" should "hard-delete the row physically" in {
    val temp = TestEntity("id-temp", now, now, "Temp", "temp@acme.com", "active")
    createDocument(temp) shouldBe true
    loadDocument("id-temp") shouldBe Some(temp)

    destroy("id-temp") shouldBe true
    loadDocument("id-temp") shouldBe None

    // Verify it's really gone (not just soft-deleted)
    searchDocuments("uuid = 'id-temp'") shouldBe Nil
  }

  it should "return false for non-existent uuid" in {
    destroy("never-existed") shouldBe false
  }
}
