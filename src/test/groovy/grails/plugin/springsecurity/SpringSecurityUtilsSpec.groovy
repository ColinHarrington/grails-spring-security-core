/* Copyright 2015 the original author or authors.
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

import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.PortResolverImpl
import org.springframework.security.web.savedrequest.DefaultSavedRequest
import org.springframework.security.web.util.matcher.AnyRequestMatcher
import org.springframework.web.filter.GenericFilterBean

import grails.plugin.springsecurity.web.SecurityRequestHolder
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@TestMixin(ControllerUnitTestMixin)
class SpringSecurityUtilsSpec extends AbstractUnitSpec {

	private static originalfilterChainMap

	def setupSpec() {
		defineBeans {
			dummyFilter(DummyFilter)
			firstDummy(DummyFilter)
			secondDummy(DummyFilter)
			defaultFilterChain(DefaultSecurityFilterChain, AnyRequestMatcher.INSTANCE, [ref('firstDummy'), ref('secondDummy')])
			springSecurityFilterChain(FilterChainProxy, ref('defaultFilterChain'))
		}
		originalfilterChainMap = applicationContext.springSecurityFilterChain.filterChainMap
	}

	def setup() {
		SpringSecurityUtils.application = grailsApplication
		SpringSecurityUtils.registerFilter 'firstDummy', 100
		SpringSecurityUtils.registerFilter 'secondDummy', 200
		def configured = SpringSecurityUtils.configuredOrderedFilters
		SpringSecurityUtils.orderedFilters.each { order, name -> configured[order] = applicationContext.getBean(name) }
		applicationContext.springSecurityFilterChain.filterChainMap = originalfilterChainMap
		SecurityRequestHolder.set request, null
	}

	void 'should retain existing chainmap'() {
		when:
		SpringSecurityUtils.clientRegisterFilter 'dummyFilter', 101
		def filterChainMap = applicationContext.springSecurityFilterChain.filterChainMap
		def filters = filterChainMap.values()[0]

		then:
		filters.size() == 3
		filters[1] == applicationContext.dummyFilter
	}

	void 'should add as first in existing chainmap'() {

		when:
		SpringSecurityUtils.clientRegisterFilter 'dummyFilter', 99
		def filterChainMap = applicationContext.springSecurityFilterChain.filterChainMap
		def filters = filterChainMap.values()[0]

		then:
		filters.size() == 3
		filters[0] == applicationContext.dummyFilter
	}

	void 'should add as last in existing chainmap'() {

		when:
		SpringSecurityUtils.clientRegisterFilter 'dummyFilter', 201
		def filterChainMap = applicationContext.springSecurityFilterChain.filterChainMap
		def filters = filterChainMap.values()[0]

		then:
		filters.size() == 3
		filters[2] == applicationContext.dummyFilter
	}

	void 'authoritiesToRoles'() {

		when:
		def roleNames = []
		def authorities = []
		(1..10).each { i ->
			String name = "role$i"
			roleNames << name
			authorities << new SimpleGrantedAuthority(name)
		}

		def roles = SpringSecurityUtils.authoritiesToRoles(authorities)

		then:
		assertSameContents roleNames, roles
	}

	void 'authoritiesToRoles() when there is an authority with a null string'() {

		when:
		def authorities = [new SimpleGrantedAuthority('role1'), new FakeAuthority()]
		SpringSecurityUtils.authoritiesToRoles(authorities)

		then:
		thrown AssertionError
	}

	void 'getPrincipalAuthorities() when not authenticated (no auth)'() {
		expect:
		!SpringSecurityUtils.principalAuthorities
	}

	void 'getPrincipalAuthorities() when not authenticated (no roles)'() {
		when:
		SecurityTestUtils.authenticate()

		then:
		!SpringSecurityUtils.principalAuthorities
	}

	void 'getPrincipalAuthorities'() {
		when:
		def authorities = (1..10).collect { new SimpleGrantedAuthority("role$it") }

		SecurityTestUtils.authenticate null, null, authorities

		then:
		authorities == SpringSecurityUtils.principalAuthorities
	}

	void 'parseAuthoritiesString'() {
		when:
		String roleNames = 'role1,role2,role3'
		def roles = SpringSecurityUtils.parseAuthoritiesString(roleNames)

		then:
		3 == roles.size()

		when:
		def expected = ['role1', 'role2', 'role3']
		def actual = roles.collect { authority -> authority.authority }

		then:
		assertSameContents expected, actual
	}

	void 'retainAll'() {
		when:
		def granted = [new SimpleGrantedAuthority('role1'),
		               new SimpleGrantedAuthority('role2'),
		               new SimpleGrantedAuthority('role3')]
		def required = [new SimpleGrantedAuthority('role1')]

		def expected = ['role1']

		then:
		assertSameContents expected, SpringSecurityUtils.retainAll(granted, required)
	}

	void 'isAjax using parameter, false'() {
		expect:
		!SpringSecurityUtils.isAjax(request)
	}

	void 'isAjax using parameter, true'() {
		when:
		request.setParameter 'ajax', 'true'

		then:
		SpringSecurityUtils.isAjax request
	}

	void 'isAjax using header, false'() {
		expect:
		!SpringSecurityUtils.isAjax(request)
	}

	void 'isAjax using header, XMLHttpRequest'() {
		when:
		request.addHeader('X-Requested-With', 'XMLHttpRequest')

		then:
		SpringSecurityUtils.isAjax request
	}

	void 'isAjax using header, true'() {
		when:
		request.addHeader 'X-Requested-With', 'true'

		then:
		!SpringSecurityUtils.isAjax(request)
	}

	void 'isAjax using SavedRequest, false'() {

		when:
		def savedRequest = new DefaultSavedRequest(request, new PortResolverImpl())
		request.session.setAttribute SpringSecurityUtils.SAVED_REQUEST, savedRequest

		then:
		!SpringSecurityUtils.isAjax(request)
	}

	void 'isAjax using SavedRequest, true'() {
		when:
		request.addHeader 'X-Requested-With', 'true'
		def savedRequest = new DefaultSavedRequest(request, new PortResolverImpl())
		request.session.setAttribute SpringSecurityUtils.SAVED_REQUEST, savedRequest

		then:
		!SpringSecurityUtils.isAjax(request)
	}

	void 'isAjax using SavedRequest, XMLHttpRequest'() {
		when:
		request.addHeader 'X-Requested-With', 'XMLHttpRequest'
		def savedRequest = new DefaultSavedRequest(request, new PortResolverImpl())
		request.session.setAttribute SpringSecurityUtils.SAVED_REQUEST, savedRequest

		then:
		SpringSecurityUtils.isAjax(request)
	}

	void 'ifAllGranted'() {
		when:
		initRoleHierarchy ''
		SecurityTestUtils.authenticate(['ROLE_1', 'ROLE_2'], true)

		then:
		SpringSecurityUtils.ifAllGranted('ROLE_1')
		SpringSecurityUtils.ifAllGranted('ROLE_2')
		SpringSecurityUtils.ifAllGranted('ROLE_1,ROLE_2')
		!SpringSecurityUtils.ifAllGranted('ROLE_1,ROLE_2,ROLE_3')
		!SpringSecurityUtils.ifAllGranted('ROLE_3')

		SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_1')])
		SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_2')])
		SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_1'), new SimpleGrantedAuthority('ROLE_2')])
		!SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_1'), new SimpleGrantedAuthority('ROLE_2'), new SimpleGrantedAuthority('ROLE_3')])
		!SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_3')])

		SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_1')])
		SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_2')])
		SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_1'), newGrantedAuthorityImpl('ROLE_2')])
		!SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_1'), newGrantedAuthorityImpl('ROLE_2'), newGrantedAuthorityImpl('ROLE_3')])
		!SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_3')])
	}

	void 'ifAllGranted using hierarchy'() {
		when:
		initRoleHierarchy 'ROLE_3 > ROLE_2 \n ROLE_2 > ROLE_1'
		SecurityTestUtils.authenticate(['ROLE_3'], true)

		then:
		SpringSecurityUtils.ifAllGranted('ROLE_1')
		SpringSecurityUtils.ifAllGranted('ROLE_2')
		SpringSecurityUtils.ifAllGranted('ROLE_1,ROLE_2')
		SpringSecurityUtils.ifAllGranted('ROLE_1,ROLE_2,ROLE_3')
		SpringSecurityUtils.ifAllGranted('ROLE_3')
		!SpringSecurityUtils.ifAllGranted('ROLE_4')

		SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_1')])
		SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_2')])
		SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_1'), new SimpleGrantedAuthority('ROLE_2')])
		SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_1'), new SimpleGrantedAuthority('ROLE_2'), new SimpleGrantedAuthority('ROLE_3')])
		SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_3')])
		!SpringSecurityUtils.ifAllGranted([new SimpleGrantedAuthority('ROLE_4')])

		SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_1')])
		SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_2')])
		SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_1'), newGrantedAuthorityImpl('ROLE_2')])
		SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_1'), newGrantedAuthorityImpl('ROLE_2'), newGrantedAuthorityImpl('ROLE_3')])
		SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_3')])
		!SpringSecurityUtils.ifAllGranted([newGrantedAuthorityImpl('ROLE_4')])
	}

	void 'ifNotGranted'() {
		when:
		initRoleHierarchy ''
		SecurityTestUtils.authenticate(['ROLE_1', 'ROLE_2'])

		then:
		!SpringSecurityUtils.ifNotGranted('ROLE_1')
		!SpringSecurityUtils.ifNotGranted('ROLE_2')
		!SpringSecurityUtils.ifNotGranted('ROLE_1,ROLE_2')
		!SpringSecurityUtils.ifNotGranted('ROLE_1,ROLE_2,ROLE_3')
		SpringSecurityUtils.ifNotGranted('ROLE_3')

		!SpringSecurityUtils.ifNotGranted([new SimpleGrantedAuthority('ROLE_1')])
		!SpringSecurityUtils.ifNotGranted([new SimpleGrantedAuthority('ROLE_2')])
		!SpringSecurityUtils.ifNotGranted([new SimpleGrantedAuthority('ROLE_1'), new SimpleGrantedAuthority('ROLE_2')])
		!SpringSecurityUtils.ifNotGranted([new SimpleGrantedAuthority('ROLE_1'), new SimpleGrantedAuthority('ROLE_2'), new SimpleGrantedAuthority('ROLE_3')])
		SpringSecurityUtils.ifNotGranted([new SimpleGrantedAuthority('ROLE_3')])

		!SpringSecurityUtils.ifNotGranted([newGrantedAuthorityImpl('ROLE_1')])
		!SpringSecurityUtils.ifNotGranted([newGrantedAuthorityImpl('ROLE_2')])
		!SpringSecurityUtils.ifNotGranted([newGrantedAuthorityImpl('ROLE_1'), newGrantedAuthorityImpl('ROLE_2')])
		!SpringSecurityUtils.ifNotGranted([newGrantedAuthorityImpl('ROLE_1'), newGrantedAuthorityImpl('ROLE_2'), newGrantedAuthorityImpl('ROLE_3')])
		SpringSecurityUtils.ifNotGranted([newGrantedAuthorityImpl('ROLE_3')])
	}

	void 'ifNotGranted using hierarchy'() {
		when:
		initRoleHierarchy 'ROLE_3 > ROLE_2 \n ROLE_2 > ROLE_1'
		SecurityTestUtils.authenticate(['ROLE_3'])

		then:
		!SpringSecurityUtils.ifNotGranted('ROLE_1')
		!SpringSecurityUtils.ifNotGranted('ROLE_2')
		!SpringSecurityUtils.ifNotGranted('ROLE_1,ROLE_2')
		!SpringSecurityUtils.ifNotGranted('ROLE_1,ROLE_2,ROLE_3')
		!SpringSecurityUtils.ifNotGranted('ROLE_3')
		SpringSecurityUtils.ifNotGranted('ROLE_4')
	}

	void 'ifAnyGranted'() {
		when:
		initRoleHierarchy ''
		SecurityTestUtils.authenticate(['ROLE_1', 'ROLE_2'])

		then:
		SpringSecurityUtils.ifAnyGranted('ROLE_1')
		SpringSecurityUtils.ifAnyGranted('ROLE_2')
		SpringSecurityUtils.ifAnyGranted('ROLE_1,ROLE_2')
		SpringSecurityUtils.ifAnyGranted('ROLE_1,ROLE_2,ROLE_3')
		!SpringSecurityUtils.ifAnyGranted('ROLE_3')
	}

	void 'ifAnyGranted using hierarchy'() {
		when:
		initRoleHierarchy 'ROLE_3 > ROLE_2 \n ROLE_2 > ROLE_1'
		SecurityTestUtils.authenticate(['ROLE_3'])

		then:
		SpringSecurityUtils.ifAnyGranted('ROLE_1')
		SpringSecurityUtils.ifAnyGranted('ROLE_2')
		SpringSecurityUtils.ifAnyGranted('ROLE_1,ROLE_2')
		SpringSecurityUtils.ifAnyGranted('ROLE_1,ROLE_2,ROLE_3')
		SpringSecurityUtils.ifAnyGranted('ROLE_3')
		!SpringSecurityUtils.ifAnyGranted('ROLE_4')
	}

	void 'private constructor'() {
		expect:
		SecurityTestUtils.testPrivateConstructor SpringSecurityUtils
	}

	void 'getSecurityConfigType'() {
		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = SecurityConfigType.Annotation

		then:
		'Annotation' == SpringSecurityUtils.securityConfigType

		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = SecurityConfigType.Annotation.name()

		then:
		'Annotation' == SpringSecurityUtils.securityConfigType

		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = 'Annotation'

		then:
		'Annotation' == SpringSecurityUtils.securityConfigType

		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = SecurityConfigType.InterceptUrlMap

		then:
		'InterceptUrlMap' == SpringSecurityUtils.securityConfigType

		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = SecurityConfigType.InterceptUrlMap.name()

		then:
		'InterceptUrlMap' == SpringSecurityUtils.securityConfigType

		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = 'InterceptUrlMap'

		then:
		'InterceptUrlMap' == SpringSecurityUtils.securityConfigType

		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = SecurityConfigType.Requestmap

		then:
		'Requestmap' == SpringSecurityUtils.securityConfigType

		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = SecurityConfigType.Requestmap.name()

		then:
		'Requestmap' == SpringSecurityUtils.securityConfigType

		when:
		SpringSecurityUtils.resetSecurityConfig()
		grailsApplication.config.grails.plugin.springsecurity.securityConfigType = 'Requestmap'

		then:
		'Requestmap' == SpringSecurityUtils.securityConfigType
	}

	/**
	 * Check that two collections contain the same data, independent of collection class and order.
	 */
	private boolean assertSameContents(c1, c2) {
		assert c1.size() == c2.size()
		assert c1.containsAll(c2)
		true
	}

	private void initRoleHierarchy(String hierarchyString) {
		defineBeans {
			roleHierarchy(RoleHierarchyImpl) {
				hierarchy = hierarchyString
			}
		}
	}

	private GrantedAuthority newGrantedAuthorityImpl(String name) {
		new org.springframework.security.core.authority.GrantedAuthorityImpl(name)
	}
}

class DummyFilter extends GenericFilterBean {
	void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {}
}

class FakeAuthority implements GrantedAuthority {
	String authority
}
