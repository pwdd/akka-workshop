// *****************************************************************************
// Projects
// *****************************************************************************

lazy val `chakka-iam` =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, DockerPlugin, JavaAppPackaging)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.akkaActorTyped,
        library.akkaClusterShardingTyped,
        library.akkaDiscoveryDns,
        library.akkaHttp,
        library.akkaHttpCirce,
        library.akkaHttpSession,
        library.akkaLog4j,
        library.akkaManagementClusterBootstrap,
        library.akkaManagementClusterHttp,
        library.akkaPersistenceCassandra,
        library.akkaPersistenceQuery,
        library.akkaPersistenceTyped,
        library.akkaStreamTyped,
        library.alpakkaSse,
        library.circeGeneric,
        library.circeParser,
        library.disruptor,
        library.jaxbApi,
        library.log4jApiScala,
        library.log4jCore,
        library.log4jSlf4jImpl,         // Needed for transient slf4j depencencies, e.g. via akak-persistence-cassandra!
        library.pureConfig,
        library.akkaHttpTestkit         % Test,
        library.akkaPersistenceInmemory % Test,
        library.akkaStreamTestkit       % Test,
        library.akkaTestkitTyped        % Test,
        library.mockitoInline           % Test,
        library.scalaCheck              % Test,
        library.utest                   % Test
      )
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val akka                     = "2.5.12"
      val akkaHttp                 = "10.1.1"
      val akkaHttpJson             = "1.20.1"
      val akkaHttpSession          = "0.5.5"
      val akkaLog4j                = "1.6.1"
      val akkaManagement           = "0.12.0"
      val akkaPersistenceCassandra = "0.84"
      val akkaPersistenceInmemory  = "2.5.1.1"
      val alpakka                  = "0.19"
      val circe                    = "0.9.3"
      val disruptor                = "3.4.2"
      val jaxb                     = "2.3.0"
      val log4j                    = "2.11.0"
      val log4jApiScala            = "11.0"
      val mockito                  = "2.18.3"
      val pureConfig               = "0.9.1"
      val scalaCheck               = "1.14.0"
      val utest                    = "0.6.4"
    }
    val akkaActorTyped                 = "com.typesafe.akka"                  %% "akka-actor-typed"                  % Version.akka
    val akkaClusterShardingTyped       = "com.typesafe.akka"                  %% "akka-cluster-sharding-typed"       % Version.akka
    val akkaDiscoveryDns               = "com.lightbend.akka.discovery"       %% "akka-discovery-dns"                % Version.akkaManagement
    val akkaHttp                       = "com.typesafe.akka"                  %% "akka-http"                         % Version.akkaHttp
    val akkaHttpCirce                  = "de.heikoseeberger"                  %% "akka-http-circe"                   % Version.akkaHttpJson
    val akkaHttpSession                = "com.softwaremill.akka-http-session" %% "core"                              % Version.akkaHttpSession
    val akkaHttpTestkit                = "com.typesafe.akka"                  %% "akka-http-testkit"                 % Version.akkaHttp
    val akkaLog4j                      = "de.heikoseeberger"                  %% "akka-log4j"                        % Version.akkaLog4j
    val akkaManagementClusterBootstrap = "com.lightbend.akka.management"      %% "akka-management-cluster-bootstrap" % Version.akkaManagement
    val akkaManagementClusterHttp      = "com.lightbend.akka.management"      %% "akka-management-cluster-http"      % Version.akkaManagement
    val akkaPersistenceCassandra       = "com.typesafe.akka"                  %% "akka-persistence-cassandra"        % Version.akkaPersistenceCassandra
    val akkaPersistenceInmemory        = "com.github.dnvriend"                %% "akka-persistence-inmemory"         % Version.akkaPersistenceInmemory
    val akkaPersistenceQuery           = "com.typesafe.akka"                  %% "akka-persistence-query"            % Version.akka
    val akkaPersistenceTyped           = "com.typesafe.akka"                  %% "akka-persistence-typed"            % Version.akka
    val akkaStreamTyped                = "com.typesafe.akka"                  %% "akka-stream-typed"                 % Version.akka
    val akkaStreamTestkit              = "com.typesafe.akka"                  %% "akka-stream-testkit"               % Version.akka
    val akkaTestkitTyped               = "com.typesafe.akka"                  %% "akka-testkit-typed"                % Version.akka
    val alpakkaSse                     = "com.lightbend.akka"                 %% "akka-stream-alpakka-sse"           % Version.alpakka
    val circeGeneric                   = "io.circe"                           %% "circe-generic"                     % Version.circe
    val circeParser                    = "io.circe"                           %% "circe-parser"                      % Version.circe
    val disruptor                      = "com.lmax"                           %  "disruptor"                         % Version.disruptor
    val jaxbApi                        = "javax.xml.bind"                     %  "jaxb-api"                          % Version.jaxb
    val log4jApiScala                  = "org.apache.logging.log4j"           %% "log4j-api-scala"                   % Version.log4jApiScala
    val log4jCore                      = "org.apache.logging.log4j"           %  "log4j-core"                        % Version.log4j
    val log4jSlf4jImpl                 = "org.apache.logging.log4j"           %  "log4j-slf4j-impl"                  % Version.log4j
    val mockitoInline                  = "org.mockito"                        %  "mockito-inline"                    % Version.mockito
    val pureConfig                     = "com.github.pureconfig"              %% "pureconfig"                        % Version.pureConfig
    val scalaCheck                     = "org.scalacheck"                     %% "scalacheck"                        % Version.scalaCheck
    val utest                          = "com.lihaoyi"                        %% "utest"                             % Version.utest
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
  scalafmtSettings ++
  headerSettings ++
  dockerSettings ++
  commandAliases

lazy val commonSettings =
  Seq(
    scalaVersion := "2.12.6",
    organization := "rocks.heikoseeberger",
    organizationName := "Heiko Seeberger",
    startYear := Some(2018),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8",
      "-Ypartial-unification",
      "-Ywarn-unused-import"
    ),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value, (Compile / javaSource).value),
    Test / unmanagedSourceDirectories := Seq((Test / scalaSource).value),
    Compile / packageDoc / publishArtifact := false,
    Compile / packageSrc / publishArtifact := false,
    testFrameworks += new TestFramework("utest.runner.Framework")
)

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true
  )

lazy val headerSettings =
  Seq(
    headerLicense := Some(HeaderLicense.Custom(
      s"""|Copyright (c) ${organizationName.value}
          |""".stripMargin
    )),
    excludeFilter.in(headerSources) := excludeFilter.in(headerSources).value || "Passwords.java"
  )

lazy val dockerSettings =
  Seq(
    Docker / daemonUser := "root",
    Docker / maintainer := "Heiko Seeberger",
    Docker / version := "latest",
    dockerBaseImage := "openjdk:10.0.1-slim",
    dockerExposedPorts := Seq(80, 19999),
    dockerRepository := Some("hseeberger")
  )

lazy val commandAliases =
  addCommandAlias(
    "r0",
    """|reStart
       |---
       |-Dchakka-iam.api.port=8080
       |-Dakka.management.http.hostname=127.0.0.1
       |-Dakka.management.http.port=20000
       |-Dakka.remote.artery.canonical.hostname=127.0.0.1
       |-Dakka.remote.artery.canonical.port=25520
       |-Dakka.cluster.seed-nodes.0=akka://chakka-iam@127.0.0.1:25520""".stripMargin
  ) ++
  addCommandAlias(
    "r1",
    """|reStart
       |---
       |-Dchakka-iam.api.port=8081
       |-Dakka.management.http.hostname=127.0.0.1
       |-Dakka.management.http.port=20001
       |-Dakka.remote.artery.canonical.hostname=127.0.0.1
       |-Dakka.remote.artery.canonical.port=25521
       |-Dakka.cluster.seed-nodes.0=akka://chakka-iam@127.0.0.1:25520""".stripMargin
 )
