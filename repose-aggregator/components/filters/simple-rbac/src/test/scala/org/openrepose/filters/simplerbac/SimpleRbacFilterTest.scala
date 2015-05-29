/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.filters.simplerbac

import java.net.URL
import javax.servlet.http.HttpServletResponse.{SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED, SC_NOT_FOUND, SC_OK}

import com.mockrunner.mock.web.MockFilterConfig
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.test.appender.ListAppender
import org.junit.runner.RunWith
import org.mockito.{Matchers, Mockito, ArgumentCaptor}
import org.openrepose.commons.config.resource.{ConfigurationResource, ConfigurationResourceResolver}
import org.openrepose.core.services.config.ConfigurationService
import org.openrepose.filters.simplerbac.config.{DelegatingType, SimpleRbacConfig}
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.springframework.mock.web.{MockFilterChain, MockHttpServletRequest, MockHttpServletResponse}

import scala.collection.JavaConversions._

@RunWith(classOf[JUnitRunner])
class SimpleRbacFilterTest extends FunSpec with BeforeAndAfterAll with BeforeAndAfter with GivenWhenThen with org.scalatest.Matchers with MockitoSugar with LazyLogging {
  var filter: SimpleRbacFilter = _
  var config: SimpleRbacConfig = _
  var servletRequest: MockHttpServletRequest = _
  var servletResponse: MockHttpServletResponse = _
  var filterChain: MockFilterChain = _
  var mockConfigService: ConfigurationService = _
  var mockFilterConfig: MockFilterConfig = _

  override def beforeAll() {
    System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
      "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl")
  }

  before {
    servletRequest = new MockHttpServletRequest
    servletResponse = new MockHttpServletResponse
    filterChain = new MockFilterChain
    mockConfigService = mock[ConfigurationService]
    mockFilterConfig = new MockFilterConfig
    filter = new SimpleRbacFilter(mockConfigService)
    config = new SimpleRbacConfig
    config.setResources(
      """
        |/path/to/this  GET       role1,role2,role3,role4
        |/path/to/this  PUT       role1,role2,role3
        |/path/to/this  POST      role1,role2
        |/path/to/this  DELETE    role1
        |/path/to/that  GET,PUT   ANY
        |/path/to/that  ALL       role1
        | """.stripMargin.trim()
    )
  }

  describe("when the configuration is updated") {
    it("should have a default Delegation Type") {
      Given("an un-initialized filter and the default configuration")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)

      When("the configuration is updated")
      filter.configurationUpdated(config)

      Then("the Delegating Type should be default")
      assert(filter.configuration.getDelegating == null)
    }
    it("should have a default Delegation Quality") {
      Given("an un-initialized filter and the default configuration")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)

      When("the Delegating Type is set with a default and the configuration is updated")
      config.setDelegating(new DelegatingType)
      filter.configurationUpdated(config)

      Then("the Delegating Quality should be default")
      assert(filter.configuration.getDelegating.getQuality == .1)
    }
    it("should have a default Roles Header Name") {
      Given("an un-initialized filter and the default configuration")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)

      When("the configuration is updated")
      filter.configurationUpdated(config)

      Then("the Roles Header Name should be default")
      assert(filter.configuration.getRolesHeaderName == "X-ROLES")
    }
    it("should have a default Enable Masking 403's") {
      Given("an un-initialized filter and the default configuration")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)

      When("the configuration is updated")
      filter.configurationUpdated(config)

      Then("the Enable Masking 403's should be default")
      assert(!filter.configuration.isEnableMasking403S)
    }
    it("should set the current configuration on the filter with the defaults initially and flag that it is initialized") {
      Given("an un-initialized filter and a modified configuration")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)
      val configuration = new SimpleRbacConfig
      val delegatingType = new DelegatingType
      delegatingType.setQuality(1.0d)
      configuration.setDelegating(delegatingType)
      configuration.setRolesHeaderName("NEW-HEADER-NAME")
      configuration.setEnableMasking403S(true)

      When("the configuration is updated")
      filter.configurationUpdated(configuration)

      Then("the filter's configuration should be modified")
      assert(filter.isInitialized)
      assert(filter.configuration == configuration)
      assert(filter.configuration.getDelegating.getQuality == 1.0d)
      assert(filter.configuration.getRolesHeaderName == "NEW-HEADER-NAME")
      assert(filter.configuration.isEnableMasking403S)
    }
  }

  describe("when initializing the filter") {
    it("should initialize the configuration to the default configuration") {
      Given("an un-initialized filter and a mock'd Filter Config")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)
      mockFilterConfig.setFilterName("SimpleRbacFilter")
      val resourceCaptor = ArgumentCaptor.forClass(classOf[URL])

      When("the filter is initialized")
      filter.init(mockFilterConfig)

      Then("the filter should register with the ConfigurationService")
      Mockito.verify(mockConfigService).subscribeTo(
        Matchers.eq("SimpleRbacFilter"),
        Matchers.eq("simple-rbac.cfg.xml"),
        resourceCaptor.capture,
        Matchers.eq(filter),
        Matchers.eq(classOf[SimpleRbacConfig]))

      assert(resourceCaptor.getValue.toString.endsWith("/META-INF/schema/config/simple-rbac.xsd"))
    }
    it("should initialize the configuration to the given configuration") {
      Given("an un-initialized filter and a mock'd Filter Config")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)
      mockFilterConfig.setInitParameter("filter-config", "another-name.cfg.xml")

      When("the filter is initialized")
      filter.init(mockFilterConfig)

      Then("the filter should register with the ConfigurationService")
      Mockito.verify(mockConfigService).subscribeTo(
        Matchers.anyString,
        Matchers.eq("another-name.cfg.xml"),
        Matchers.any(classOf[URL]),
        Matchers.any(classOf[SimpleRbacFilter]),
        Matchers.eq(classOf[SimpleRbacConfig]))
    }
  }

  describe("when destroying the filter") {
    it("should unregister the configuration from the configuration service") {
      Given("an un-initialized filter and a mock'd Filter Config")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)
      mockFilterConfig.setInitParameter("filter-config", "another-name.cfg.xml")

      When("the filter is initialized and destroyed")
      filter.init(mockFilterConfig)
      filter.destroy()

      Then("the filter should unregister with the ConfigurationService")
      Mockito.verify(mockConfigService).unsubscribeFrom("another-name.cfg.xml", filter)
    }
  }

  describe("when the configured resources list has a bad line") {
    it("should log the bad line") {
      Given("an un-initialized filter and a configuration with a malformed resource in the list")
      val ctx = LogManager.getContext(false).asInstanceOf[LoggerContext]
      val listAppender = ctx.getConfiguration.getAppender("List0").asInstanceOf[ListAppender].clear
      assert(filter.configuration == null)
      assert(!filter.isInitialized)
      config.setResources(
        """
          |/path/to/good  ALL       ANY
          |/path/to/bad   ALL
          | """.stripMargin.trim()
      )

      When("the configuration is updated")
      filter.configurationUpdated(config)

      Then("the filter's configuration should be modified")
      assert(filter.isInitialized)
      val events = listAppender.getEvents.toList.map(_.getMessage.getFormattedMessage)
      events.count(_.contains("Malformed RBAC Resource: ")) shouldBe 1
    }
  }

  describe("when the configured resources are in a file") {
    it("should load the file") {
      Given("an un-initialized filter and a configuration with the resources in a file")
      val ctx = LogManager.getContext(false).asInstanceOf[LoggerContext]
      val listAppender = ctx.getConfiguration.getAppender("List0").asInstanceOf[ListAppender].clear
      assert(filter.configuration == null)
      assert(!filter.isInitialized)
      val configuration = new SimpleRbacConfig
      val rbacFileName = "/rbac/test.rbac"
      configuration.setResourcesFileName(rbacFileName)
      val resourceResolver: ConfigurationResourceResolver = mock[ConfigurationResourceResolver]
      val configurationResource: ConfigurationResource = mock[ConfigurationResource]
      org.mockito.Mockito.when(configurationResource.newInputStream).thenReturn(this.getClass.getResourceAsStream(rbacFileName))
      org.mockito.Mockito.when(resourceResolver.resolve(rbacFileName)).thenReturn(configurationResource)
      org.mockito.Mockito.when(mockConfigService.getResourceResolver).thenReturn(resourceResolver)

      When("the configuration is updated")
      filter.configurationUpdated(configuration)

      Then("the filter's configuration should be modified")
      assert(filter.isInitialized)
      val events = listAppender.getEvents.toList.map(_.getMessage.getFormattedMessage)
      events.count(_.contains("Malformed RBAC Resource: /path/to/file")) shouldBe 1
    }

    it("should set the current configuration on the filter with the defaults initially and flag that it is initialized") {
      Given("an un-initialized filter and a modified configuration")
      assert(filter.configuration == null)
      assert(!filter.isInitialized)
      val configuration = new SimpleRbacConfig
      val delegatingType = new DelegatingType
      delegatingType.setQuality(1.0d)
      configuration.setDelegating(delegatingType)
      configuration.setRolesHeaderName("NEW-HEADER-NAME")
      configuration.setEnableMasking403S(true)

      When("the configuration is updated")
      filter.configurationUpdated(configuration)

      Then("the filter's configuration should be modified")
      assert(filter.isInitialized)
      assert(filter.configuration == configuration)
      assert(filter.configuration.getDelegating.getQuality == 1.0d)
      assert(filter.configuration.getRolesHeaderName == "NEW-HEADER-NAME")
      assert(filter.configuration.isEnableMasking403S)
    }
  }

  List(false, true).foreach { case (isMasked) =>
    val maskWith: Boolean => String = { boolean => if (boolean) "with" else "without" }
    describe(s"Simple RBAC ${maskWith(isMasked)} masked roles") {
      val resultShould: Int => String = { int => if (int == SC_OK) "should" else "should not" }
      val conditionsThis: List[(String, String, Int, Int)] = List(
        //Method    Role      Result        Masked
        ("GET",     "role1",  SC_OK,        SC_OK),
        ("PUT",     "role1",  SC_OK,        SC_OK),
        ("POST",    "role1",  SC_OK,        SC_OK),
        ("DELETE",  "role1",  SC_OK,        SC_OK),
        ("GET",     "role2",  SC_OK,        SC_OK),
        ("PUT",     "role2",  SC_OK,        SC_OK),
        ("POST",    "role2",  SC_OK,        SC_OK),
        ("DELETE",  "role2",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("GET",     "role3",  SC_OK,        SC_OK),
        ("PUT",     "role3",  SC_OK,        SC_OK),
        ("POST",    "role3",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("DELETE",  "role3",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("GET",     "role4",  SC_OK,        SC_OK),
        ("PUT",     "role4",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("POST",    "role4",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("DELETE",  "role4",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("GET",     "role5",  SC_FORBIDDEN, SC_NOT_FOUND),
        ("PUT",     "role5",  SC_FORBIDDEN, SC_NOT_FOUND),
        ("POST",    "role5",  SC_FORBIDDEN, SC_NOT_FOUND),
        ("DELETE",  "role5",  SC_FORBIDDEN, SC_NOT_FOUND)
      )
      conditionsThis.foreach { case (method, role, result, masked) =>
        it(s"${resultShould(result)} allow the request to THIS resource when using HTTP method $method and having role $role.") {
          Given(s"a request using HTTP method $method and having role $role")
          servletRequest.setRequestURI("/path/to/this")
          servletRequest.setMethod(method)
          servletRequest.addHeader(config.getRolesHeaderName, role)
          config.setEnableMasking403S(isMasked)
          filter.configurationUpdated(config)

          When("the protected resource is requested")
          filter.doFilter(servletRequest, servletResponse, filterChain)

          Then(s"the request ${resultShould(result)} be allowed access")
          if (isMasked) {
            servletResponse.getStatus shouldBe masked
          } else {
            servletResponse.getStatus shouldBe result
          }
        }
      }

      val conditionsThat: List[(String, String, Int, Int)] = List(
        //Method    Role      Result        Masked
        ("GET",     "role1",  SC_OK,        SC_OK),
        ("PUT",     "role1",  SC_OK,        SC_OK),
        ("POST",    "role1",  SC_OK,        SC_OK),
        ("DELETE",  "role1",  SC_OK,        SC_OK),
        ("GET",     "role2",  SC_OK,        SC_OK),
        ("PUT",     "role2",  SC_OK,        SC_OK),
        ("POST",    "role2",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("DELETE",  "role2",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED)
      )
      conditionsThat.foreach { case (method, role, result, masked) =>
        it(s"${resultShould(result)} allow the request to THAT resource when using HTTP method $method and having role $role.") {
          Given("a request with roles")
          servletRequest.setRequestURI("/path/to/that")
          servletRequest.setMethod(method)
          servletRequest.addHeader(config.getRolesHeaderName, role)
          config.setEnableMasking403S(isMasked)
          filter.configurationUpdated(config)

          When("the request is to access a protected resource")
          filter.doFilter(servletRequest, servletResponse, filterChain)

          Then(s"the request ${resultShould(result)} be allowed access")
          if (isMasked) {
            servletResponse.getStatus shouldBe masked
          } else {
            servletResponse.getStatus shouldBe result
          }
        }
      }

      val conditionsNone: List[(String, Int, Int)] = List(
        //Method    Result        Masked
        ("GET",     SC_OK,        SC_OK),
        ("PUT",     SC_OK,        SC_OK),
        ("POST",    SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("DELETE",  SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED)
      )
      conditionsNone.foreach { case (method, result, masked) =>
        it(s"${resultShould(result)} allow the request to THAT resource when using HTTP method $method and having no roles.") {
          Given("a request without roles")
          servletRequest.setRequestURI("/path/to/that")
          servletRequest.setMethod(method)
          config.setEnableMasking403S(isMasked)
          filter.configurationUpdated(config)

          When("the request is to access a protected resource")
          filter.doFilter(servletRequest, servletResponse, filterChain)

          Then(s"the request ${resultShould(result)} be allowed access")
          if (isMasked) {
            servletResponse.getStatus shouldBe masked
          } else {
            servletResponse.getStatus shouldBe result
          }
        }
      }

      val conditionsMulti: List[(String, String, String, Int, Int)] = List(
        //Method    RolesA          RolesB          Result        Masked
        ("GET",     "role1, roleA", "role5, roleB", SC_OK,        SC_OK),
        ("PUT",     "role1, roleA", "role5, roleB", SC_OK,        SC_OK),
        ("POST",    "role1, roleA", "role5, roleB", SC_OK,        SC_OK),
        ("DELETE",  "role1, roleA", "role5, roleB", SC_OK,        SC_OK),
        ("GET",     "role4, roleA", "role5, roleB", SC_OK,        SC_OK),
        ("PUT",     "role4, roleA", "role5, roleB", SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("POST",    "role4, roleA", "role5, roleB", SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED),
        ("DELETE",  "role4, roleA", "role5, roleB", SC_FORBIDDEN, SC_METHOD_NOT_ALLOWED)
      )
      conditionsMulti.foreach { case (method, rolesa, rolesb, result, masked) =>
        it(s"${resultShould(result)} allow the request to THIS resource when using HTTP method $method and having the roles $rolesa and $rolesb.") {
          Given("a request multiple roles")
          servletRequest.setRequestURI("/path/to/this")
          servletRequest.setMethod(method)
          servletRequest.addHeader(config.getRolesHeaderName, rolesa)
          servletRequest.addHeader(config.getRolesHeaderName, rolesb)
          config.setEnableMasking403S(isMasked)
          filter.configurationUpdated(config)

          When("the request is to access a protected resource")
          filter.doFilter(servletRequest, servletResponse, filterChain)

          Then(s"the request ${resultShould(result)} be allowed access")
          if (isMasked) {
            servletResponse.getStatus shouldBe masked
          } else {
            servletResponse.getStatus shouldBe result
          }
        }
      }
    }
  }
}
