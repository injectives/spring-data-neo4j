/*
 * Copyright 2011-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.integration.properties;

import static org.assertj.core.api.Assertions.assertThat;

import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.config.AbstractReactiveNeo4jConfig;
import org.springframework.data.neo4j.core.ReactiveNeo4jTemplate;
import org.springframework.data.neo4j.test.Neo4jExtension;
import org.springframework.data.neo4j.test.Neo4jIntegrationTest;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Michael J. Simons
 * @soundtrack Metallica - Metallica
 */
@Neo4jIntegrationTest
class ReactivePropertyIT {

	protected static Neo4jExtension.Neo4jConnectionSupport neo4jConnectionSupport;

	@BeforeAll
	static void setupData(@Autowired Driver driver) {

		try (Session session = driver.session()) {
			session.run("MATCH (n) DETACH DELETE n").consume();
		}
	}

	@Autowired
	private Driver driver;
	@Autowired
	private ReactiveNeo4jTemplate template;

	@Test // GH-2118
	void assignedIdNoVersionShouldNotOverwriteUnknownProperties() {

		try (Session session = driver.session()) {
			session.run(
					"CREATE (m:SimplePropertyContainer {id: 'id1', knownProperty: 'A', unknownProperty: 'Mr. X'}) RETURN id(m)")
					.consume();
		}

		updateKnownAndAssertUnknownProperty(DomainClasses.SimplePropertyContainer.class, "id1");
	}

	@Test // GH-2118
	void assignedIdWithVersionShouldNotOverwriteUnknownProperties() {

		try (Session session = driver.session()) {
			session.run(
					"CREATE (m:SimplePropertyContainerWithVersion {id: 'id1', version: 1, knownProperty: 'A', unknownProperty: 'Mr. X'}) RETURN id(m)")
					.consume();
		}

		updateKnownAndAssertUnknownProperty(DomainClasses.SimplePropertyContainerWithVersion.class, "id1");
	}

	@Test // GH-2118
	void generatedIdNoVersionShouldNotOverwriteUnknownProperties() {

		Long id;
		try (Session session = driver.session()) {
			id = session
					.run("CREATE (m:SimpleGeneratedIDPropertyContainer {knownProperty: 'A', unknownProperty: 'Mr. X'}) RETURN id(m)")
					.single().get(0).asLong();
		}

		updateKnownAndAssertUnknownProperty(DomainClasses.SimpleGeneratedIDPropertyContainer.class, id);
	}

	@Test // GH-2118
	void generatedIdWithVersionShouldNotOverwriteUnknownProperties() {

		Long id;
		try (Session session = driver.session()) {
			id = session
					.run("CREATE (m:SimpleGeneratedIDPropertyContainerWithVersion {version: 1, knownProperty: 'A', unknownProperty: 'Mr. X'}) RETURN id(m)")
					.single().get(0).asLong();
		}

		updateKnownAndAssertUnknownProperty(DomainClasses.SimpleGeneratedIDPropertyContainerWithVersion.class, id);
	}

	private void updateKnownAndAssertUnknownProperty(Class<? extends DomainClasses.BaseClass> type, Object id) {

		template.findById(id, type)
				.map(m -> {
					m.setKnownProperty("A2");
					return m;
				})
				.flatMap(template::save)
				.as(StepVerifier::create)
				.expectNextMatches(c -> "A2".equals(c.getKnownProperty()))
				.verifyComplete();

		try (Session session = driver.session()) {
			long cnt = session
					.run("MATCH (m:" + type.getSimpleName() + ") WHERE " + (id instanceof Long ? "id(m) " : "m.id")
						 + " = $id AND m.knownProperty = 'A2' AND m.unknownProperty = 'Mr. X' RETURN count(m)",
							Collections.singletonMap("id", id)).single().get(0).asLong();
			assertThat(cnt).isEqualTo(1L);
		}
	}

	@Test // GH-2118
	void multipleAssignedIdNoVersionShouldNotOverwriteUnknownProperties() {

		try (Session session = driver.session()) {
			session.run(
					"CREATE (m:SimplePropertyContainer {id: 'a', knownProperty: 'A', unknownProperty: 'Fix'})  RETURN id(m)")
					.consume();
			session.run(
					"CREATE (m:SimplePropertyContainer {id: 'b', knownProperty: 'B', unknownProperty: 'Foxy'}) RETURN id(m)")
					.consume();
		}

		template.findById("a", DomainClasses.SimplePropertyContainer.class)
				.zipWith(template.findById("b", DomainClasses.SimplePropertyContainer.class))
				.flatMapMany(t -> {
					t.getT1().setKnownProperty("A2");
					t.getT2().setKnownProperty("B2");
					return template.saveAll(t.toList());
				})
				.as(StepVerifier::create)
				.expectNextCount(2)
				.verifyComplete();

		try (Session session = driver.session()) {
			long cnt = session
					.run("MATCH (m:SimplePropertyContainer) WHERE m.id in $ids AND m.unknownProperty IS NOT NULL RETURN count(m)",
							Collections.singletonMap("ids", Arrays.asList("a", "b"))).single().get(0).asLong();
			assertThat(cnt).isEqualTo(2L);
		}
	}

	@Test // GH-2118
	void relationshipPropertiesMustNotBeOverwritten() {

		Long id;
		try (Session session = driver.session()) {
			id = session
					.run("CREATE (a:IrrelevantSourceContainer) - [:RELATIONSHIP_PROPERTY_CONTAINER {knownProperty: 'A', unknownProperty: 'Mr. X'}] -> (:IrrelevantTargetContainer) RETURN id(a)")
					.single().get(0).asLong();
		}

		template.findById(id, DomainClasses.IrrelevantSourceContainer.class)
				.map(c -> {
					c.getRelationshipPropertyContainer().setKnownProperty("A2");
					return c;
				})
				.flatMap(template::save)
				.as(StepVerifier::create)
				.expectNextMatches(c -> "A2".equals(c.getRelationshipPropertyContainer().getKnownProperty()))
				.verifyComplete();

		try (Session session = driver.session()) {
			long cnt = session
					.run("MATCH (m) - [r:RELATIONSHIP_PROPERTY_CONTAINER] -> (:IrrelevantTargetContainer) WHERE id(m) = $id AND r.knownProperty = 'A2' AND r.unknownProperty = 'Mr. X' RETURN count(m)",
							Collections.singletonMap("id", id)).single().get(0).asLong();
			assertThat(cnt).isEqualTo(1L);
		}
	}

	@Test // GH-2118
	void relationshipIdsShouldBeFilled() {

		DomainClasses.RelationshipPropertyContainer rel = new DomainClasses.RelationshipPropertyContainer();
		rel.setKnownProperty("A");
		rel.setIrrelevantTargetContainer(new DomainClasses.IrrelevantTargetContainer());

		DomainClasses.RelationshipPropertyContainerNoId relB = new DomainClasses.RelationshipPropertyContainerNoId();
		relB.setKnownProperty("B");
		relB.setIrrelevantTargetContainer(new DomainClasses.IrrelevantTargetContainer());

		List<DomainClasses.IrrelevantSourceContainer> recorded = new ArrayList<>();
		template.save(new DomainClasses.IrrelevantSourceContainer(rel, relB))
				.as(StepVerifier::create)
				.recordWith(() -> recorded)
				//.expectNextMatches(i -> i.getRelationshipPropertyContainer().getId() != null)
				.verifyComplete();

		try (Session session = driver.session()) {
			long cnt = session.run("MATCH (m) - [r:RELATIONSHIP_PROPERTY_CONTAINER] -> (:IrrelevantTargetContainer) WHERE id(m) = $id AND r.knownProperty = 'A' RETURN count(m)",
					Collections.singletonMap("id", recorded.get(0).getId())).single().get(0).asLong();
			assertThat(cnt).isEqualTo(1L);
		}
	}

	@Configuration
	@EnableTransactionManagement
	static class Config extends AbstractReactiveNeo4jConfig {

		@Bean
		public Driver driver() {
			return neo4jConnectionSupport.getDriver();
		}
	}
}