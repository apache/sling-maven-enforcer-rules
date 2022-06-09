/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
File buildLog = new File(basedir, 'build.log')
assert buildLog.text.contains('Found 17 missing runtime dependencies')
assert buildLog.text.contains('[WARNING] Dependency org.osgi:org.osgi.framework:jar:1.8.0 (provided) via org.apache.jackrabbit.vault:vault-cli:jar:3.6.0 -> org.apache.jackrabbit.vault:org.apache.jackrabbit.vault:jar:3.6.0 not found as runtime dependency!')
assert buildLog.text.contains('[WARNING] Dependency com.google.code.findbugs:jsr305:jar:3.0.2 (provided) not found as runtime dependency!')
assert buildLog.text.contains('[WARNING] Found provided dependency org.apache.jackrabbit:oak-jackrabbit-api:jar:1.42.0 only with potentially incompatible version 1.40.0 in runtime classpath')
assert true