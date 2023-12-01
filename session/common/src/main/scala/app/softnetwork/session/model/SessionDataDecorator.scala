package app.softnetwork.session.model

trait SessionDataDecorator[T <: SessionData] extends SessionDataKeys { self: T =>

  protected var dirty: Boolean = false

  def isDirty: Boolean = dirty

  def clear(): T with SessionDataDecorator[T] =
    synchronized {
      val theId = id
      dirty = true
      withData(Map.empty[String, String] + (idKey -> theId))
    }

  def get(key: String): Option[String] = data.get(key)

  def apply(key: String): Any = data(key)

  def isEmpty: Boolean = data.isEmpty

  def contains(key: String): Boolean = data.contains(key)

  def withData(data: Map[String, String]): T with SessionDataDecorator[T]

  def withRefreshable(refreshable: Boolean): T with SessionDataDecorator[T]

  def -(key: String): T with SessionDataDecorator[T] =
    synchronized {
      dirty = true
      withData(data - key)
    }

  def +(kv: (String, String)): T with SessionDataDecorator[T] =
    synchronized {
      dirty = true
      withData(data + kv)
    }

  def ++(kvs: Seq[(String, String)]): T with SessionDataDecorator[T] =
    synchronized {
      dirty = true
      withData(data ++ kvs)
    }

  lazy val id: String = data(idKey)

  def withId(id: String): T with SessionDataDecorator[T] = {
    synchronized {
      dirty = true
      withData(data + (idKey -> id))
    }
  }

  lazy val clientId: Option[String] = get(clientIdKey)

  def withClientId(clientId: String): T with SessionDataDecorator[T] = {
    synchronized {
      dirty = true
      withData(data + (clientIdKey -> clientId))
    }
  }

  lazy val admin: Boolean = get(adminKey).exists(_.toBoolean)

  def withAdmin(admin: Boolean): T with SessionDataDecorator[T] = {
    synchronized {
      dirty = true
      withData(data + (adminKey -> admin.toString))
    }
  }

  lazy val anonymous: Boolean = get(anonymousKey).exists(_.toBoolean)

  def withAnonymous(anonymous: Boolean): T with SessionDataDecorator[T] = {
    synchronized {
      dirty = true
      withData(data + (anonymousKey -> anonymous.toString))
    }
  }

  lazy val profile: Option[String] = get(profileKey)

  def withProfile(profile: String): T with SessionDataDecorator[T] = {
    synchronized {
      dirty = true
      withData(data + (profileKey -> profile))
    }
  }
}
