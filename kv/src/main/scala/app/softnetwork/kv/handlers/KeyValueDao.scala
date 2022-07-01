package app.softnetwork.kv.handlers

import app.softnetwork.kv.persistence.typed.KeyValueBehavior

trait KeyValueDao extends KvDao with KvHandler with KeyValueBehavior

object KeyValueDao extends KeyValueDao
