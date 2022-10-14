package app.softnetwork.persistence.auth.service

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.StatusCodes
import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.serialization._
import app.softnetwork.persistence.auth.config.Settings
import app.softnetwork.persistence.auth.handlers.{AccountKeyDao, MockGenerator}
import app.softnetwork.persistence.auth.message._
import app.softnetwork.persistence.auth.model.{AccountStatus, AccountView}
import app.softnetwork.api.server.config.Settings._
import app.softnetwork.persistence.auth.scalatest.BasicAccountRouteTestKit

/**
  * Created by smanciot on 22/03/2018.
  */
class SecurityRoutesSpec extends AnyWordSpecLike with BasicAccountRouteTestKit {

  private val anonymous = "anonymous"

  private val username = "smanciot"

  private val firstName = Some("Stephane")

  private val lastName = Some("Manciot")

  private val email = "stephane.manciot@gmail.com"

  private val gsm = "33660010203"

  private val password = "Changeit1"

  "MainRoutes" should {
    "contain a healthcheck path" in {
      Get(s"/$RootPath/healthcheck") ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Anonymous SignUp" should {
    "work" in {
      Post(s"/$RootPath/${Settings.Path}/anonymous") ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Created
        val account = responseAs[AccountView]
        account.status shouldBe AccountStatus.Active
        account.anonymous.getOrElse(false) shouldBe true
        cookies = extractCookies(headers)
      }
    }
  }

  "SignUp" should {
    "work with anonymous account" in {
      withCookies(Post(s"/$RootPath/${Settings.Path}/signUp", SignUp(anonymous, password, None, firstName, lastName)))  ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Created
        val account = responseAs[AccountView]
        account.status shouldBe AccountStatus.Active
        account.anonymous.getOrElse(true) shouldBe false
      }
    }
    "fail if confirmed password does not match password" in {
      Post(s"/$RootPath/${Settings.Path}/signUp", SignUp(username, password, Some("fake"))) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe PasswordsNotMatched.message
      }
    }
    "work with username" in {
      Post(s"/$RootPath/${Settings.Path}/signUp", SignUp(username, password, None, firstName, lastName))  ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail if username already exists" in {
      Post(s"/$RootPath/${Settings.Path}/signUp", SignUp(username, password))  ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe LoginAlreadyExists.message
      }
    }
    "work with email" in {
      Post(s"/$RootPath/${Settings.Path}/signUp", SignUp(email, password, None, firstName, lastName))  ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AccountView].status shouldBe AccountStatus.Inactive
      }
    }
    "fail if email already exists" in {
      Post(s"/$RootPath/${Settings.Path}/signUp", SignUp(email, password))  ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
    "work with gsm" in {
      Post(s"/$RootPath/${Settings.Path}/signUp", SignUp(gsm, password, None, firstName, lastName))  ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail if gsm already exists" in {
      Post(s"/$RootPath/${Settings.Path}/signUp", SignUp(gsm, password))  ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
  }

  "basic" should {
    "work with matching username and password" in {
      val validCredentials = BasicHttpCredentials(username, password)
      Post(s"/$RootPath/${Settings.Path}/basic") ~> addCredentials(validCredentials) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login(username, password)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "work with matching email and password" in {
      AccountKeyDao.lookupAccount(email)(typedSystem()) await {
        case Some(uuid) =>
          Get(s"/$RootPath/${Settings.Path}/activate", Activate(MockGenerator.computeToken(uuid))) ~> mainRoutes(typedSystem())
          Post(s"/$RootPath/${Settings.Path}/login", Login(email, password)) ~> mainRoutes(typedSystem()) ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[AccountView].status shouldBe AccountStatus.Active
          }
        case _          => fail()
      }
    }
    "work with matching gsm and password" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login(gsm, password)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail with unknown username" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login("fake", password)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown email" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login("fake@gmail.com", password)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown gsm" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login("0102030405", password)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching username and password" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login(username, "fake")) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching email and password" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login(email, "fake")) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching gsm and password" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login(gsm, "fake")) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "disable account after n login failures" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login(gsm, password)) ~> mainRoutes(typedSystem())  // reset number of failures
      val failures = (0 to Settings.MaxLoginFailures) // max number of failures + 1
          .map(_ => Post(s"/$RootPath/${Settings.Path}/login", Login(gsm, "fake")) ~> mainRoutes(typedSystem()) )
      failures.last ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual AccountDisabled.message
      }
    }
  }

  "ResetPassword" should {
    "work" in {
      Post(s"/$RootPath/${Settings.Path}/verificationCode", SendVerificationCode(gsm)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
        cookies = extractCookies(headers)
      }
      withCookies(Post(s"/$RootPath/${Settings.Path}/resetPassword", ResetPassword(MockGenerator.code, password))) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Logout" should {
    "work" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login(gsm, password, refreshable = true)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
        cookies = extractCookies(headers)
      }
      withCookies(Post(s"/$RootPath/${Settings.Path}/logout")) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Unsubscribe" should {
    "work" in {
      Post(s"/$RootPath/${Settings.Path}/login", Login(gsm, password, refreshable = true)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
        cookies = extractCookies(headers)
      }
      withCookies(Post(s"/$RootPath/${Settings.Path}/unsubscribe")) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldEqual AccountStatus.Deleted
      }
    }
  }

  "SendVerificationCode" should {
    "work with email" in {
      Post(s"/$RootPath/${Settings.Path}/verificationCode", SendVerificationCode(email)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "work with gsm" in {
      Post(s"/$RootPath/${Settings.Path}/verificationCode", SendVerificationCode(gsm)) ~> mainRoutes(typedSystem()) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

}
