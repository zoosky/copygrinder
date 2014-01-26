organization  := "org.copygrinder"

version       := "0.1"

scalaVersion  := "2.10.3"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/",
  "JGit repo" at "https://repo.eclipse.org/content/groups/releases/",
  "OSS" at "https://oss.sonatype.org/content/repositories/snapshots"
)

/* SCALA LIBS */
libraryDependencies ++= Seq(
  "io.spray"            %   "spray-can"         % "1.2.0",
  "io.spray"            %   "spray-routing"     % "1.2.0",
  "io.spray"            %   "spray-testkit"     % "1.2.0",
  "io.spray" 			%%  "spray-json" 		% "1.2.5",
  "com.typesafe.akka"   %%  "akka-actor"        % "2.2.3",
  "com.typesafe.akka"   %%  "akka-testkit"      % "2.2.3"
)

/* JAVA LIBS */
libraryDependencies ++= Seq(
  "org.eclipse.jgit"   	%   "org.eclipse.jgit"  % "3.1.0.201310021548-r",
  "commons-io"          %   "commons-io"        % "2.4"
)

/* TEST LIBS */
libraryDependencies ++= Seq(
  "org.specs2"          %%  "specs2"          	% "2.2.3"    	% "test",
  "org.scalatest" 		%   "scalatest_2.10"    % "2.0"      	% "test",
  "org.mockito"         %   "mockito-core"     	% "1.9.5"    	% "test",
  "junit" 				% 	"junit" 			% "4.11" 		% "test"
)

seq(Revolver.settings: _*)

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil

EclipseKeys.withSource := true

fork := true
