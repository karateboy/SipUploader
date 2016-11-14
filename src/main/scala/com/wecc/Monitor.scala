package com.wecc

case class Monitor(id: String, sip_id: String)
object Monitor {
  val list = List(
    Monitor("D001", "台塑六輕工業園區#彰化縣大城站"),
    Monitor("D003", "台塑六輕工業園區#嘉義縣東石站"),
    Monitor("D005", "台塑六輕工業園區#雲林縣褒忠站"),
    Monitor("D006", "台塑六輕工業園區#雲林縣崙背站"),
    Monitor("D007", "台塑六輕工業園區#雲林縣四湖站"),
    Monitor("D008", "台塑六輕工業園區#雲林縣東勢站"),
    Monitor("D009", "台塑六輕工業園區#雲林縣麥寮站"),
    Monitor("D109", "台塑六輕工業園區#雲林縣麥寮站"),
    Monitor("D010", "台塑六輕工業園區#雲林縣台西站"),
    Monitor("D110", "台塑六輕工業園區#雲林縣台西站"),
    Monitor("D011", "台塑六輕工業園區#雲林縣土庫站"),
    Monitor("D012", "台塑六輕工業園區#雲林縣西螺站"))

  val map = {
    val pair =
      list map { mt => mt.id -> mt }

    pair.toMap
  }

}