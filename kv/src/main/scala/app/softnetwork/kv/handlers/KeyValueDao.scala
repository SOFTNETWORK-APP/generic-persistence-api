package app.softnetwork.kv.handlers

import app.softnetwork.kv.persistence.typed.KeyValueBehavior
import org.slf4j.{Logger, LoggerFactory}

trait KeyValueDao extends KvDao with KvHandler with KeyValueBehavior

object KeyValueDao extends KeyValueDao{
  lazy val log: Logger = LoggerFactory getLogger getClass.getName
}
