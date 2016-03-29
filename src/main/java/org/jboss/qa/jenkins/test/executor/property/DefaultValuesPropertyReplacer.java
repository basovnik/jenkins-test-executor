/*
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.qa.jenkins.test.executor.property;

import java.util.LinkedList;
import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class DefaultValuesPropertyReplacer implements PropertyReplacer {

	private final char boundaryCharacter;
	@Singular
	private List<PropertyResolver> resolvers = new LinkedList<>();

	public static DefaultValuesPropertyReplacerBuilder builder() {
		return new DefaultValuesPropertyReplacerBuilder().boundaryCharacter('$');
	}

	public DefaultValuesPropertyReplacer resolvers(PropertyResolver resolver) {
		this.resolvers.add(resolver);
		return this;
	}

	private enum State {
		INITIAL, BOUNDARY, OPEN_BRACE, DEFAULT;
	}

	private boolean isBoundary(int ch) {
		return boundaryCharacter == ch;
	}

	private boolean isOpenBrace(int ch) {
		return '{' == ch;
	}

	private boolean isCloseBrace(int ch) {
		return '}' == ch;
	}

	private boolean isDefaultValueDelimiter(int ch) {
		return ':' == ch;
	}

	@Override
	public String replace(String expression) {
		State state = State.INITIAL;
		StringBuilder property = null;
		StringBuilder defaultValue = null;
		final StringBuilder result = new StringBuilder();

		for (int i = 0; i < expression.length(); i = expression.offsetByCodePoints(i, 1)) {
			final int ch = expression.codePointAt(i);
			switch (state) {
				case INITIAL:
					if (isBoundary(ch)) {
						state = State.BOUNDARY;
					} else {
						result.appendCodePoint(ch);
					}
					continue;

				case BOUNDARY:
					property = null; // property can be read, null previous value
					if (isBoundary(ch)) { // boundary character escaping
						result.appendCodePoint(ch);
						state = State.INITIAL;
					} else if (isOpenBrace(ch)) {
						property = new StringBuilder();
						state = State.OPEN_BRACE;
					} else { // bad character, inform and ignore boundary
						log.warn("Use of boundary character for property is invalid, will be ignored!");
						result.appendCodePoint(ch);
						state = State.INITIAL;
					}
					continue;

				case OPEN_BRACE:
					defaultValue = null; // default value can be read, null previous value
					if (isDefaultValueDelimiter(ch)) {
						defaultValue = new StringBuilder();
						state = State.DEFAULT;
					} else if (isCloseBrace(ch)) { // resolve property without default value
						final String value = resolve(property.toString());
						if (value != null) {
							result.append(value);
						}
						state = State.INITIAL;
					} else {
						property.appendCodePoint(ch);
					}
					continue;

				case DEFAULT:
					if (isCloseBrace(ch)) { // resolve property with default value
						final String value = resolve(property.toString());
						result.append(value != null ? value : defaultValue);
						state = State.INITIAL;
					} else {
						defaultValue.appendCodePoint(ch);
					}
					continue;

				default:
					throw new IllegalStateException();
			}
		}

		if (state != State.INITIAL && state != State.BOUNDARY) { // Incomplete expression && ignore trailing boundary
			throw new IllegalStateException("Incomplete expression, close brace is missing: " + result);
		}

		return result.toString();
	}

	private String resolve(String name) {
		for (PropertyResolver resolver : resolvers) {
			final String value = resolver.resolve(name);
			if (value != null) {
				return value;
			}
		}
		log.warn("Property '{}' was not resolved", name);
		return null;
	}
}
