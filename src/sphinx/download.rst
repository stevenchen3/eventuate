.. _download:

--------
Download
--------

Eventuate is a multi-module project with the following modules:

.. list-table:: Table 1: Eventuate modules
   :header-rows: 1

   * - Module
     - Description
   * - ``eventuate-core``
     - Core module, required for all applications.
   * - ``eventuate-crdt``
     - Provides :ref:`commutative-replicated-data-types`.
   * - ``eventuate-log-cassandra``
     - Provides the :ref:`cassandra-storage-backend`.
   * - ``eventuate-log-leveldb``
     - Provides the :ref:`leveldb-storage-backend`.
   * - ``eventuate-adapter-stream``
     - Provides the :ref:`akka-streams-adapter`.
   * - ``eventuate-adapter-vertx``
     - Provides the :ref:`vertx-adapter`.
   * - ``eventuate-adapter-spark``
     - Provides the :ref:`spark-adapter`.

|

.. note::
   An Eventuate application requires at least ``eventuate-core`` and one storage backend module as dependency.

Binaries
--------

Release binaries for Scala 2.11 and 2.12 are published to Bintray_, snapshot binaries to OJO_ (oss.jfrog.org).

.. note::
   ``eventuate-adapter-spark`` is only available for Scala 2.11 at the moment.

Maven
~~~~~

Latest release dependencies (for ``SCALA_VERSION``\ s ``2.11`` and ``2.12``)::

    <repository>
        <id>eventuate-releases</id>
        <name>Eventuate Releases</name>
        <url>https://dl.bintray.com/rbmhtechnology/maven</url>
    </repository>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-core_{SCALA_VERSION}</artifactId>
        <version>0.8.1</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-crdt_{SCALA_VERSION}</artifactId>
        <version>0.8.1</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-log-leveldb_{SCALA_VERSION}</artifactId>
        <version>0.8</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-log-cassandra_{SCALA_VERSION}</artifactId>
        <version>0.8.1</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-adapter-stream_{SCALA_VERSION}</artifactId>
        <version>0.8.1</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-adapter-vertx_{SCALA_VERSION}</artifactId>
        <version>0.8.1</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-adapter-spark_2.11</artifactId>
        <version>0.8.1</version>
    </dependency>

Development snapshot dependencies (for ``SCALA_VERSION``\ s ``2.11`` and ``2.12``)::

    <repository>
        <id>ojo-snapshots</id>
        <name>OJO Snapshots</name>
        <url>https://oss.jfrog.org/oss-snapshot-local</url>
    </repository>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-core_{SCALA_VERSION}</artifactId>
        <version>0.9-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-crdt_{SCALA_VERSION}</artifactId>
        <version>0.9-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-log-leveldb_{SCALA_VERSION}</artifactId>
        <version>0.9-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-log-cassandra_{SCALA_VERSION}</artifactId>
        <version>0.9-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-adapter-stream_{SCALA_VERSION}</artifactId>
        <version>0.9-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-adapter-vertx_{SCALA_VERSION}</artifactId>
        <version>0.9-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>com.rbmhtechnology</groupId>
        <artifactId>eventuate-adapter-spark_2.11</artifactId>
        <version>0.9-SNAPSHOT</version>
    </dependency>

SBT
~~~

Latest release dependencies::

    resolvers += "Eventuate Releases" at "https://dl.bintray.com/rbmhtechnology/maven"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-core" % "0.8.1"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-crdt" % "0.8.1"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-log-leveldb" % "0.8.1"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-log-cassandra" % "0.8.1"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-adapter-stream" % "0.8.1"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-adapter-vertx" % "0.8.1"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-adapter-spark" % "0.8.1"

Development snapshot dependencies::

    resolvers += "OJO Snapshots" at "https://oss.jfrog.org/oss-snapshot-local"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-core" % "0.9-SNAPSHOT"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-crdt" % "0.9-SNAPSHOT"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-log-leveldb" % "0.9-SNAPSHOT"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-log-cassandra" % "0.9-SNAPSHOT"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-adapter-stream" % "0.9-SNAPSHOT"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-adapter-vertx" % "0.9-SNAPSHOT"

    libraryDependencies += "com.rbmhtechnology" %% "eventuate-adapter-spark" % "0.9-SNAPSHOT"

Sources
-------

To download the Eventuate sources, clone the `Github repository`_. Source jar files are also published to Bintray_ and OJO_.

.. _OJO: http://oss.jfrog.org/artifactory/simple/oss-snapshot-local/
.. _Bintray: https://bintray.com/rbmhtechnology/maven/eventuate
.. _Github repository: https://github.com/RBMHTechnology/eventuate

.. _sbt: http://www.scala-sbt.org/
