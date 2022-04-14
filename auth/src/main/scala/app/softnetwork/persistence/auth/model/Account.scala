package app.softnetwork.persistence.auth.model

import java.util.Date

import app.softnetwork.persistence.model.Timestamped

import app.softnetwork.serialization._

import app.softnetwork.persistence._


trait Account extends Principals with AccountDecorator with Timestamped {
  def credentials: String
  def lastLogin: Option[Date]
  def nbLoginFailures: Int
  def status: AccountStatus

  def email: Option[String] = get(PrincipalType.Email).map(_.value)
  def gsm: Option[String] = get(PrincipalType.Gsm).map(_.value)
  def username: Option[String] = get(PrincipalType.Username).map(_.value)

  def details: Option[AccountDetails]

  def verificationToken: Option[VerificationToken]
  def verificationCode: Option[VerificationCode]

  def registrations: Seq[DeviceRegistration]

  def principal: Principal

  def secondaryPrincipals: Seq[Principal]

  def currentProfile: Option[Profile]

  def profiles: Map[String, Profile]

  def isAdmin: Boolean = profiles.values.exists(_.`type` == ProfileType.ADMINISTRATOR)

  def isCustomer: Boolean = profiles.values.exists(_.`type` == ProfileType.CUSTOMER)

  def isSeller: Boolean = profiles.values.exists(_.`type` == ProfileType.SELLER)

  def isVendor: Boolean = profiles.values.exists(_.`type` == ProfileType.VENDOR)

  def newProfile(name: String): Profile
}

trait AccountDetails extends Timestamped {
  def firstName: String
  def lastName: String
  def phoneNumber: Option[String]
  def email: Option[String]
  def userName: Option[String]
}

@SerialVersionUID(0L)
class AccountView(val lastLogin: Option[Date],
                  val status: AccountStatus,
                  val createdDate: Date,
                  val lastUpdated: Date,
                  val currentProfile: Option[Profile],
                  val details: Option[AccountDetails])

/**
  * A collection of all principals associated with a corresponding Subject.
  * A principal is just a security term for an identifying attribute, such as
  * a username or user id or social security number or anything else that can be considered an 'identifying' attribute
  * for a Subject
  *
  */
trait Principals extends Profiles {self: Account =>

  def withSecondaryPrincipals(secondaryPrincipals: Seq[Principal]): Account

  /**
    * primary principal
    */
  val primaryPrincipal: Principal = Principal(PrincipalType.Uuid, uuid)

  /**
    *
    * @return all principals within this collection of principals
    */
  def principals: Seq[Principal] = Seq(primaryPrincipal) ++ secondaryPrincipals

  /**
    *
    * @param principal - the principal to add to this collection of principals
    * @return the collection of principals
    */
  def add(principal: Principal): Account = {
    if(principal.`type` != PrincipalType.Uuid){
      (if(self.principal.`type` == principal.`type`){
        self.withPrincipal(principal)
      }
      else{
        self
      }).withSecondaryPrincipals(secondaryPrincipals.filterNot(_.`type` == principal.`type`)  :+ principal)
    }
    else{
      self
    }
  }

  def addAllPrincipals(principals: List[Principal]): Account = {
    principals match {
      case Nil        => this
      case head::tail =>
        add(head)
        addAllPrincipals(tail)
    }
  }

  /**
    *
    * @param `type` - type of Principal to look for within this collection of principals
    * @return Some[Principal] if a principal of this type has been found, None otherwise
    */
  def get(`type`: PrincipalType): Option[Principal] = principals.find(_.`type` == `type`)
}

trait PrincipalCompanion{
  def apply(principal: String): Principal = {
    if(EmailValidator.check(principal)){
      Principal(PrincipalType.Email, principal)
    }
    else if(GsmValidator.check(principal)){
      Principal(PrincipalType.Gsm, principal)
    }
    else{
      Principal(PrincipalType.Username, principal)
    }
  }
}

trait Profiles {self: Account =>

  def withCurrentProfile(currentProfile: Profile): Account

  def withProfiles(profiles: Map[String, Profile]): Account

  /**
    * add a profile
    *
    * @param profile - the profile to add
    */
  def add(profile: Profile): Account = {
    val completedProfile = completeProfile(profile)
    (currentProfile match {
      case None => withCurrentProfile(completedProfile)
      case _    => self
    }).copyWithDetails(Some(completedProfile)).withProfiles(profiles.+(profile.name -> completedProfile))
  }

  def completeProfile(profile: Profile): Profile = {
    self.details match {
      case Some(details) =>
        import details.{email => _, _}
        profile.withUuid(
          self.uuid
        ).withCreatedDate(
          now()
        ).withLastUpdated(
          now()
        ).withFirstName(
          profile.firstName.getOrElse(firstName.getOrElse(""))
        ).withLastName(
          profile.lastName.getOrElse(lastName.getOrElse(""))
        ).withPhoneNumber(
          profile.phoneNumber.getOrElse(phoneNumber.getOrElse(gsm.orNull))
        ).withEmail(
          profile.email.getOrElse(details.email.getOrElse(self.email.orNull))
        ).withUserName(
          profile.userName.getOrElse(userName.getOrElse(self.username.orNull))
        )
      case _ =>
        profile.withEmail(
          profile.email.getOrElse(self.email.orNull)
        ).withPhoneNumber(
          profile.phoneNumber.getOrElse(self.gsm.orNull)
        ).withUserName(
          profile.userName.getOrElse(self.username.orNull)
        )
    }
  }

  /**
    * add all profiles
    *
    * @param profiles - the profiles to add
    * @return
    */
  def addAllProfiles(profiles: List[Profile]): Account = {
    profiles match {
      case Nil        => self
      case head::tail => add(head).addAllProfiles(tail)
    }
  }

  /**
    * set current profile
    *
    * @param name - profile unique name
    */
  def setCurrentProfile(name: String): Account = {
    profile(Some(name)) match {
      case Some(p) => copyWithDetails(Some(p)).withCurrentProfile(p)
      case _ => add(newProfile(name)).setCurrentProfile(name)
    }
  }

  def profile(name: Option[String]): Option[Profile] =
    (name match {
      case Some(key) => profiles.get(key) match {
        case s: Some[Profile] => s
        case _                => add(newProfile(key)).profile(name)
      }
      case _         => currentProfile
    }).map(profile => profile.copyWithAccount(self))

}

trait Profile extends AccountDetails with ProfileDecorator {
  /**
    * profile name (should be unique) among all account profiles
    *
    * @return
    */
  def name: String
  def `type`: ProfileType
  def description: Option[String]
}

@SerialVersionUID(0L)
class ProfileView(
                   val uuid: String,
                   val createdDate: Date,
                   val lastUpdated: Date,
                   val firstName: String,
                   val lastName: String,
                   val phoneNumber: Option[String],
                   val email: Option[String],
                   val userName: Option[String],
                   val name: String,
                   val `type`: ProfileType,
                   val description: Option[String]) extends AccountDetails {
}

trait AccountDecorator {account: Account =>
  def withPrincipal(principal: Principal): Account
  def withCredentials(credentials: String): Account
  def withLastLogin(lastLogin: Date): Account
  def withLastLogout(lastLogout: Date): Account
  def withNbLoginFailures(nbLoginFailures: Int): Account
  def withStatus(status: AccountStatus): Account
  def withVerificationToken(verificationToken: VerificationToken): Account
  def withVerificationCode(verificationCode: VerificationCode): Account
  def withLastUpdated(lastUpdated: Date): Account
  def withDetails(details: AccountDetails): Account
  def withRegistrations(registrations: Seq[DeviceRegistration]): Account

  def copyWithCredentials(credentials: String): Account = withCredentials(credentials)
  def copyWithLastLogin(lastLogin: Option[Date]): Account = withLastLogin(lastLogin.orNull)
  def copyWithNbLoginFailures(nbLoginFailures: Int): Account = withNbLoginFailures(nbLoginFailures)
  def copyWithStatus(status: AccountStatus): Account = withStatus(status)
  def copyWithVerificationToken(verificationToken: Option[VerificationToken]): Account =
    withVerificationToken(verificationToken.orNull)
  def copyWithVerificationCode(verificationCode: Option[VerificationCode]): Account =
    withVerificationCode(verificationCode.orNull)
  def copyWithLastUpdated(maybeLastUpdated: Option[Date]): Account =
    maybeLastUpdated  match {
      case Some(lastUpdated) => withLastUpdated(lastUpdated)
      case _ => account
    }
  def copyWithDetails(details: Option[AccountDetails]): Account = {
    (details match {
      case Some(s) if s.phoneNumber.isDefined =>
        if(GsmValidator.check(s.phoneNumber.get)){
          add(Principal(s.phoneNumber.get))
        }
        else{
          account
        }
      case Some(s) if s.email.isDefined =>
        if(EmailValidator.check(s.email.get)){
          add(Principal(s.email.get))
        }
        else{
          account
        }
      case Some(s) if s.userName.isDefined => add(Principal(s.userName.get))
      case _ => account
    }).withDetails(details.orNull)
  }
  def copyWithRegistrations(registrations: Seq[DeviceRegistration]): Account = withRegistrations(registrations)

  val view: AccountView =
    new AccountView(
      account.lastLogin,
      account.status,
      account.createdDate,
      account.lastUpdated,
      account.currentProfile,
      account.details
    )
}

trait ProfileDecorator { profile: Profile =>
  def withUuid(uuid: String): Profile
  def withCreatedDate(createdDate: Date): Profile
  def withLastUpdated(lastUpdated: Date): Profile
  def withName(name: String): Profile
  def withType(`type`: ProfileType): Profile
  def withFirstName(firstName: String): Profile
  def withLastName(lastName: String): Profile
  def withPhoneNumber(phoneNumber: String): Profile
  def withEmail(email: String): Profile
  def withUserName(userName: String): Profile
  def withDescription(description: String): Profile

  def copyWithAccount(account: Account): Profile = {
    copyWithDetails(account.details)
  }

  def copyWithDetails(details: Option[AccountDetails]): Profile = {
    details match {
      case Some(s) =>
        withFirstName(s.firstName.getOrElse(firstName.getOrElse("")))
          .withLastName(s.lastName.getOrElse(lastName.getOrElse("")))
          .withPhoneNumber(s.phoneNumber.getOrElse(phoneNumber.orNull))
          .withEmail(s.email.getOrElse(email.orNull))
          .withUserName(s.userName.getOrElse(userName.orNull))
          .withCreatedDate(s.createdDate)
          .withLastUpdated(s.lastUpdated)
          .withUuid(s.uuid)
      case _ => this
    }
  }

  def copyWithPrincipal(principal: Principal): Profile = {
    principal.`type` match {
      case PrincipalType.Gsm => withPhoneNumber(principal.value)
      case PrincipalType.Email => withEmail(principal.value)
      case PrincipalType.Username => withUserName(principal.value)
      case _ => this
    }
  }

  val view: ProfileView =
    new ProfileView(
      uuid = profile.uuid,
      createdDate = profile.createdDate,
      lastUpdated = profile.lastUpdated,
      name = profile.name,
      firstName = profile.firstName,
      lastName = profile.lastName,
      phoneNumber = profile.phoneNumber,
      email = profile.email,
      userName = profile.userName,
      `type` = profile.`type`,
      description = profile.description
    )
}
