package org.tmt.sample.http

import csw.location.api.codec.LocationCodecs
import io.bullet.borer.Codec
import io.bullet.borer.compat.AkkaHttpCompat
import io.bullet.borer.derivation.MapBasedCodecs.deriveCodec
import org.tmt.sample.core.models.{AdminGreetResponse, GreetResponse, UserInfo}

// #for-docs-snippet
object HttpCodecs extends HttpCodecs
// #for-docs-snippet

trait HttpCodecs extends AkkaHttpCompat with LocationCodecs {
  implicit lazy val greetResponseCodec: Codec[GreetResponse]           = deriveCodec
  implicit lazy val adminGreetResponseCodec: Codec[AdminGreetResponse] = deriveCodec
  implicit lazy val userInfoCodec: Codec[UserInfo]                     = deriveCodec
}
