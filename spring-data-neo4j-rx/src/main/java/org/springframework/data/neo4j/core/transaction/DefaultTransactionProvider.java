/*
 * Copyright (c) 2019 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.core.transaction;

import java.util.Optional;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Transaction;

/**
 * A transaction provider that opens a new session using default parameters and starting an explicit transaction on that.
 *
 * @author Michael J. Simons
 */
public class DefaultTransactionProvider implements NativeTransactionProvider {

	@Override
	public Optional<Transaction> retrieveTransaction(Driver driver) {
		return Optional.of(driver.session(Neo4jTransactionUtils.defaultSessionParameters(null)).beginTransaction());
	}
}
