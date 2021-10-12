/*
 * Copyright (c) 2021 dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reposilite.maven

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.reposilite.maven.api.METADATA_FILE
import com.reposilite.maven.api.Metadata
import com.reposilite.shared.safeResolve
import com.reposilite.shared.toPath
import com.reposilite.web.http.ErrorResponse
import panda.std.Result

internal class MetadataService(
    private val repositoryService: RepositoryService
) {

    private val xml = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) })
        .configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        .enable(INDENT_OUTPUT)
        .registerKotlinModule()
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun saveMetadata(repository: String, gav: String, metadata: Metadata): Result<Metadata, ErrorResponse> =
        repositoryService.findRepository(repository)
            .flatMap { it.putFile(gav.toPath().safeResolve(METADATA_FILE), xml.writeValueAsBytes(metadata).inputStream()) }
            .map { metadata }

    fun findVersions(repository: Repository, gav: String): Result<List<String>, ErrorResponse> =
        repository.getFile(gav.toPath().safeResolve(METADATA_FILE))
            .map { it.use { data -> xml.readValue<Metadata>(data) } }
            .map { it.versioning?.versions ?: emptyList() }
            .map { VersionComparator.sortStrings(it) }

    fun findLatest(repository: Repository, gav: String): Result<String, ErrorResponse> =
        findVersions(repository, gav)
            .map { it.last() }

}