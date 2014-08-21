package com.rackspace.papi.components.keystone.v3.objects

case class AuthenticateResponse(expires_at: String, issued_at: String, methods: Option[List[String]] = None, domain: Option[DomainsForAuthenticateResponse] = None, project: Option[ProjectForAuthenticateResponse] = None, catalog: Option[Catalog] = None, roles: Option[List[Role]] = None, user: Option[UserForAuthenticateResponse] = None) extends Serializable

case class AuthIdentityRequest(methods: List[String], password: Option[PasswordCredentials] = None, token: Option[TokenCredentials] = None) extends Serializable

case class AuthRequest(identity: AuthIdentityRequest, scope: Option[Scope] = None) extends Serializable

case class AuthRequestRoot(auth: AuthRequest) extends Serializable

case class AuthResponse(token: AuthenticateResponse) extends Serializable

case class Catalog(service: List[ServiceForAuthenticationResponse]) extends Serializable

case class Domain(id: Option[String] = None, name: Option[String] = None, enabled: Option[Boolean] = None) extends Serializable

case class DomainScope(id: String, name: String) extends Serializable

case class DomainsForAuthenticateResponse(id: Option[String] = None, name: Option[String] = None, enabled: Option[Boolean] = None) extends Serializable

case class Endpoint(id: String, name: String, interface: Option[String] = None, region: Option[String] = None, url: String, service_id: Option[String] = None) extends Serializable

case class Endpoints(endpoints: Option[List[Endpoint]] = None) extends Serializable

case class PasswordCredentials(user: UserNamePasswordRequest) extends Serializable

case class ProjectForAuthenticateResponse(domain: Option[DomainsForAuthenticateResponse] = None, id: Option[String] = None, name: Option[String] = None, enabled: Option[Boolean] = None) extends Serializable

case class ProjectScope(domain: Option[DomainScope] = None, id: String, name: Option[String] = None) extends Serializable

case class Role(id: Option[String] = None, name: String, project_id: Option[String] = None, domain_id: Option[String] = None, description: String) extends Serializable

case class Scope(domain: Option[DomainScope] = None, project: Option[ProjectScope] = None, trust: Option[TrustScope] = None) extends Serializable

case class ServiceForAuthenticationResponse(endpoints: Option[List[Endpoints]] = None, openstackType: String, id: Option[String] = None) extends Serializable

case class TokenCredentials(id: String) extends Serializable

case class TrustScope(id: String) extends Serializable

case class UserForAuthenticateResponse(domain: Option[DomainsForAuthenticateResponse] = None, id: Option[String] = None, name: Option[String] = None, description: Option[String] = None, default_project_id: Option[String] = None) extends Serializable

case class UserNamePasswordRequest(domain: Option[Domain] = None, id: Option[String] = None, name: Option[String] = None, password: String, scope: Option[Scope] = None) extends Serializable