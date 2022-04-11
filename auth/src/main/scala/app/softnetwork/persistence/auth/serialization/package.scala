package app.softnetwork.persistence.auth

import app.softnetwork.protobuf.ScalaPBSerializers
import ScalaPBSerializers.GeneratedEnumSerializer

import app.softnetwork.serialization._

import app.softnetwork.notification.serialization._

import app.softnetwork.persistence.auth.model._

/**
  * Created by smanciot on 16/05/2018.
  */
package object serialization {

  val authFormats =
    notificationFormats ++
      Seq(
        GeneratedEnumSerializer(AccountStatus.enumCompanion),
        GeneratedEnumSerializer(ProfileType.enumCompanion),
        GeneratedEnumSerializer(PrincipalType.enumCompanion)
      )

  object Protobuf {
    import scalapb.TypeMapper

    implicit val accountDetailsTypeMapper: TypeMapper[BasicAccountDetails, AccountDetails] =
      new TypeMapper[BasicAccountDetails, AccountDetails] {
        override def toBase(custom: AccountDetails): BasicAccountDetails =
          BasicAccountDetails.defaultInstance
            .withCreatedDate(custom.createdDate)
            .withFirstName(custom.firstName)
            .withLastName(custom.lastName)
            .withLastUpdated(custom.lastUpdated)
            .withPhoneNumber(custom.phoneNumber)
            .withUuid(custom.uuid)

        override def toCustom(base: BasicAccountDetails): AccountDetails = base
      }

    implicit val accountTypeMapper: TypeMapper[BasicAccount, Account] =
      new TypeMapper[BasicAccount, Account] {
        override def toBase(custom: Account): BasicAccount =
          custom match {
            case base: BasicAccount => base
            case _ =>
              import custom._
              BasicAccount.defaultInstance
                .withCreatedDate(createdDate)
                .withCredentials(credentials)
                .withCurrentProfile(currentProfile.orNull)
                .withDetails(details.orNull)
                .withLastLogin(lastLogin.orNull)
                .withLastUpdated(lastUpdated)
                .withNbLoginFailures(nbLoginFailures)
                .withPrincipal(principal)
                .withProfiles(profiles)
                .withRegistrations(registrations)
                .withSecondaryPrincipals(secondaryPrincipals)
                .withStatus(status)
                .withUuid(uuid)
                .withVerificationCode(verificationCode.orNull)
                .withVerificationToken(verificationToken.orNull)
          }

        override def toCustom(base: BasicAccount): Account = base
      }

    implicit val profileTypeMapper: TypeMapper[BasicAccountProfile, Profile] = new TypeMapper[BasicAccountProfile, Profile] {
      override def toBase(custom: Profile): BasicAccountProfile = {
        custom match {
          case base: BasicAccountProfile => base
          case _ =>
            import custom._
            BasicAccountProfile.defaultInstance
              .withCreatedDate(createdDate)
              .withDescription(description.orNull)
              .withEmail(email.orNull)
              .withFirstName(firstName)
              .withLastName(lastName)
              .withLastUpdated(lastUpdated)
              .withName(name)
              .withPhoneNumber(phoneNumber.orNull)
              .withType(`type`)
              .withUuid(uuid)
        }
      }

      override def toCustom(base: BasicAccountProfile): Profile = base
    }
  }
}
