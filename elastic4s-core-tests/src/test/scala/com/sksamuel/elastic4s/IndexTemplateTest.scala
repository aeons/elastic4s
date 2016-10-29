package com.sksamuel.elastic4s2

import java.util

import com.sksamuel.elastic4s.analyzers.StandardAnalyzerDefinition
import com.sksamuel.elastic4s.testkit.ElasticSugar
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.FieldType.StringType
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

/** @author Stephen Samuel */
class IndexTemplateTest extends WordSpec with MockitoSugar with ElasticSugar with Matchers {

  "create template" should {
    "be stored" in {

      client.execute {
        create template "brewery_template" pattern "te*" mappings (
          mapping name "brewery" as (
            "year_founded" withType StringType analyzer "test_analyzer"
            )
          ) analysis Seq(StandardAnalyzerDefinition("test_analyzer")) indexSetting("index.cache.query.enable", true)
      }.await

      val resp = client.execute {
        get template "brewery_template"
      }.await

      resp.getIndexTemplates.get(0).name shouldBe "brewery_template"
      resp.getIndexTemplates.get(0).template() shouldBe "te*"

      val source = resp.getIndexTemplates.get(0).getMappings.valuesIt().next().toString

      source should include( """"year_founded":{"type":"string"""")

      val settings = resp.getIndexTemplates.get(0).getSettings
      settings.get("index.cache.query.enable") should equal("true")

    }
    "apply template to new indexes" in {

      client.execute {
        create index "templatetest"
      }.await

      client.execute {
        index into "templatetest" / "brewery" fields (
          "year_founded" -> 1829
          )
      }.await

      blockUntilCount(1, "templatetest", "brewery")

      client.execute {
        search in "templatetest" / "brewery" query termQuery("year_founded", 1829)
      }.await.getHits.totalHits shouldBe 1

      val mappings = client.execute {
        get mapping "templatetest" / "brewery"
      }.await.mappings.get("templatetest").get("brewery")

      val year_founded = mappings.sourceAsMap().get("properties").asInstanceOf[util.Map[String, Any]]
        .get("year_founded").asInstanceOf[util.Map[String, Any]]

      // note: this field would be long if the template wasn't applied, because we index an int.
      // but the template should be applied to override it to string
      year_founded.get("type") shouldBe "string"
    }
    "support template before any index creation" in {

      client.execute {
        create template "malbec" pattern "malbec*" mappings (
          mapping name "user" fields (
            field name "distance" typed StringType
            )
          )
      }.await

      client.execute {
        index into "malbec" / "user" fields (
          "distance" -> 1234
          )
      }.await
    }
  }
}