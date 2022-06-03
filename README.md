[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-maven-enforcer-rules/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-maven-enforcer-rules/job/master/)
[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-maven-enforcer-rules/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-maven-enforcer-rules/job/master/test/?width=800&height=600)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-maven-enforcer-rules&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-rewriter)
[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-rewriter&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-maven-enforcer-rules)
[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/maven-enforcer-rules.svg)](https://www.javadoc.io/doc/org.apache.sling/maven-enforcer-rules)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/maven-enforcer-rules/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22maven-enforcer-rules%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Maven Enforcer Rules

This module is part of the [Apache Sling](https://sling.apache.org) project.

It provides additional [Maven Enforcer](https://maven.apache.org/enforcer/maven-enforcer-plugin/) rules.

## Rules

### Require Provided Dependencies in Runtime Classpath

Checks that the runtime classpath (e.g. used by Maven Plugins via the 
[Plugin Classloader](https://maven.apache.org/guides/mini/guide-maven-classloading.html#3-plugin-classloaders) or by the [Appassembler Maven Plugin's `assemble` goal](http://www.mojohaus.org/appassembler/appassembler-maven-plugin/assemble-mojo.html)) contains all [provided dependencies](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope) both direct and transitive ones.

As those are not transitively inherited they need to be declared explicitly in the pom.xml of the using Maven project.

#### Parameters

 * `excludes` - a list of dependencies to skip. Their transitive dependencies are not evaluated either. The format is `groupId[:artifactId][:version][:type][:scope][:classifier]` where `artifactId`, `version`, `type`, `scope` and `classifier` are optional. Wild cards (`*` for arbitrarily many characters or `?` for an arbitrary single character) may be used to replace an entire or just parts of a section. *Examples*: 
     * `org.apache.maven` (everything with the given group)
     * `org.apache.maven:myArtifact`
     * `org.apache.maven:*:1.2` (exclude version 1.2 and above, equivalent to [1.2,) )
     * `org.apache.maven:*:[1.2]` (explicit exclude of version 1.2)

#### Sample Plugin Configuration:

```
<project>
  [...]
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
        <version>3.0.0</version>
        <dependencies>
          <dependency>
            <groupId>org.apache.sling</groupId>
            <artifactId>maven-enforcer-rules</artifactId>
            <version>LATEST</version>
          </dependency>
        </dependencies>
        <executions>
          <execution>
            <id>enforce-complete-runtime-classpath</id>
            <goals>
              <goal>enforce</goal>
            </goals>
            <configuration>
              <rules>
                <requireProvidedDependenciesInRuntimeClasspath implementation="org.apache.sling.maven.enforcer.RequireProvidedDependenciesInRuntimeClasspath">
                  <excludes>
                    <exclude>javax.servlet:javax.servlet-api</exclude>
                  </excludes>
                </requireProvidedDependenciesInRuntimeClasspath>
              </rules>
              <fail>true</fail>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
  [...]
</project>
```