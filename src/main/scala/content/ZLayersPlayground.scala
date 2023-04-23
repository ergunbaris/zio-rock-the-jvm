package content


import zio.*
import zio.ZIO
import zio.console.*

import java.io.IOException
import java.util.TimerTask


object ZLayersPlayground extends zio.App {
  // ZIO[-R, +E , +A] = "effects" R= input E = error A = output
  // R => Either[E, A]

  val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)
  val aFailure: IO[String, Nothing] = ZIO.fail("an error")

  val greeting = for {
    _ <- putStrLn("Hi name please")
    name <- getStrLn
    _ <- putStrLn(s"Hello $name")
  } yield ()


  case class User(name: String, email: String)

  object UserEmailer {

    type UserEmailerEnv = Has[UserEmailer.Service]

    // service def
    trait Service {
      def notify(user: User, message: String): Task[Unit] /* ZIO[ANy, Throwable, Unit] */

    }

    // service impl
    val live: ULayer[UserEmailerEnv] = ZLayer.succeed(new Service {
      override def notify(user: User, message: String) = Task {
        println(s"[UserEmailer] Sending $message to ${user.email}")
      }
    })

    // front-facing API
    def notify(user: User, message: String): ZIO[UserEmailerEnv, Throwable, Unit] =
      ZIO.accessM[UserEmailerEnv](hasService => hasService.get.notify(user, message))
  }

  object UserDb {
    // env
    type UserDbEnv = Has[UserDb.Service]

    // service def
    trait Service {
      def insert(user: User): Task[Unit]
    }

    //    service impl
    val live: ULayer[UserDbEnv] = ZLayer.succeed(new Service {
      override def insert(user: User): Task[Unit] = Task {
        println(s"[Database] Inserting $user to db")
      }
    })

    // front facing api
    def insert(user: User): ZIO[UserDbEnv, Throwable, Unit] =
      ZIO.accessM(_.get.insert(user))
  }

  // HORIZONTAL COMPOSITION
  // ZLayer[In1, E1, Out1] ++ Zlayer[In2, E2, Out2] => Zlayer[In1 with In2, super(E1, E2), Out1 with Out2]

  import UserDb.UserDbEnv
  import UserEmailer.UserEmailerEnv

  val userBackendLayer: ZLayer[Any, Nothing, UserDbEnv with UserEmailerEnv] = UserDb.live ++ UserEmailer.live

  // VERTICAL COMPOSITION
  object UserSubscription {
    type UserSubscriptionEnv = Has[UserSubscription.Service]
    class Service(notifier: UserEmailer.Service, userDb: UserDb.Service) {
      def subscribe(user: User): Task[User] = for {
        _ <- userDb.insert(user)
        _ <- notifier.notify(user, s"Welcome ${user.name}")
      } yield (user)
    }

    val live = ZLayer.fromServices[UserEmailer.Service, UserDb.Service, UserSubscription.Service] {
      (userEmailer, userDb) => new Service(userEmailer, userDb)
    }
    // front-facing
    def subscribe(user: User): ZIO[UserSubscriptionEnv, Throwable, User] =
      ZIO.accessM(_.get.subscribe(user))
  }
  import UserSubscription._
  val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscriptionEnv] = userBackendLayer >>> UserSubscription.live

  val boris = User("boris", "boris_email")

//  override def run(args: List[String]) =
//    UserDb.insert(boris)
//      .provideLayer(userBackendLayer)
//      .exitCode

  override def run(args: List[String]) =
    UserSubscription.subscribe(boris)
      .provideLayer(userSubscriptionLayer)
      .exitCode


}
