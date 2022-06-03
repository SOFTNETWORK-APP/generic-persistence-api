package app.softnetwork.payment.persistence

import app.softnetwork.kv.handlers.KvDao

package object data {
  lazy val paymentKvDao: KvDao = KvDao("payment")
}
