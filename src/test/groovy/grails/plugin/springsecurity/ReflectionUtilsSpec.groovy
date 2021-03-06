/* Copyright 2006-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class ReflectionUtilsSpec extends AbstractUnitSpec {

	void 'set config property'() {
		when:
		def foo = SpringSecurityUtils.securityConfig.foo

		then:
		foo instanceof Map
		!foo

		when:
		ReflectionUtils.setConfigProperty 'foo', 'bar'

		then:
		'bar' == SpringSecurityUtils.securityConfig.foo
	}

	void 'get config property'() {
		when:
		def d = ReflectionUtils.getConfigProperty('a.b.c')

		then:
		d instanceof Map
		!d

		when:
		ReflectionUtils.setConfigProperty 'a.b.c', 'd'

		then:
		'd' == ReflectionUtils.getConfigProperty('a.b.c')
		'd' == SpringSecurityUtils.securityConfig.a.b.c
	}

	void 'get role authority'() {
		when:
		String authorityName = 'ROLE_FOO'
		def role = [authority: authorityName]

		then:
		authorityName == ReflectionUtils.getRoleAuthority(role)
	}

	void 'get requestmap url'() {
		when:
		String url = '/admin/**'
		def requestmap = [url: url]

		then:
		url == ReflectionUtils.getRequestmapUrl(requestmap)
	}

	void 'get requestmap config attribute'() {
		when:
		String configAttribute = 'ROLE_ADMIN'
		def requestmap = [configAttribute: configAttribute]

		then:
		configAttribute == ReflectionUtils.getRequestmapConfigAttribute(requestmap)
	}

	void 'as list'() {
		when:
		def list = ReflectionUtils.asList(null)

		then:
		list instanceof List
		!list

		when:
		list = ReflectionUtils.asList([1,2,3])

		then:
		list instanceof List
		3 == list.size()

		when:
		String[] strings = ['a', 'b']
		list = ReflectionUtils.asList(strings)

		then:
		list instanceof List
		2 == list.size()
	}

	void 'split map'() {
		when:
		def map = [a: 'b', c: ['d', 'e']]
		List<InterceptedUrl> split = ReflectionUtils.splitMap(map)

		then:
		2 == split.size()

/*		for (InterceptedUrl iu in split) {
			assert key instanceof String
			assert value instanceof List
		}
		assert ['b'] == split.a
		assert ['d', 'e'] == split.c
*/	}

	void 'get grails serverURL when set'() {
		when:
		String url = 'http://somewhere.org'
		ReflectionUtils.application.config.grails.serverURL = url

		then:
		ReflectionUtils.getGrailsServerURL() == url
	}

	void 'get grails serverURL when not set'() {
		when:
		ReflectionUtils.application.config.grails.serverURL = null

		then:
		ReflectionUtils.getGrailsServerURL() == null
	}
}
