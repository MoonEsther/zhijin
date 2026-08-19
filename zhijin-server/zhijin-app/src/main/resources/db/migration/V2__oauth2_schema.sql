-- OAuth2 授权服务器表（oauth2_registered_client / oauth2_authorization / oauth2_authorization_consent）
-- 来源：spring-security-oauth2-authorization-server 官方 schema（jar 内 oauth2-*-schema.sql）适配 PostgreSQL：
--   - timestamp -> TIMESTAMP WITH TIME ZONE（等价 PG timestamptz，精确存储时间瞬间）
--   - blob -> text（PG 无 blob 类型）
-- 说明：JdbcRegisteredClientRepository 生成的客户端 id 为 UUID 字符串，故 id 使用 varchar(100)。

CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id varchar(100) NOT NULL,
    client_id varchar(100) NOT NULL,
    client_id_issued_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret varchar(200) DEFAULT NULL,
    client_secret_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    client_name varchar(200) NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types varchar(1000) NOT NULL,
    redirect_uris varchar(1000) DEFAULT NULL,
    post_logout_redirect_uris varchar(1000) DEFAULT NULL,
    scopes varchar(1000) NOT NULL,
    client_settings varchar(2000) NOT NULL,
    token_settings varchar(2000) NOT NULL,
    PRIMARY KEY (id)
);
-- 按 client_id 唯一约束：JdbcRegisteredClientRepository 按 client_id 查询，防重复注册。
CREATE UNIQUE INDEX IF NOT EXISTS uk_oauth2_registered_client_client_id ON oauth2_registered_client (client_id);

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id varchar(100) NOT NULL,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000) DEFAULT NULL,
    attributes text DEFAULT NULL,
    state varchar(500) DEFAULT NULL,
    authorization_code_value text DEFAULT NULL,
    authorization_code_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    authorization_code_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    authorization_code_metadata text DEFAULT NULL,
    access_token_value text DEFAULT NULL,
    access_token_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    access_token_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    access_token_metadata text DEFAULT NULL,
    access_token_type varchar(100) DEFAULT NULL,
    access_token_scopes varchar(1000) DEFAULT NULL,
    oidc_id_token_value text DEFAULT NULL,
    oidc_id_token_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    oidc_id_token_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    oidc_id_token_metadata text DEFAULT NULL,
    refresh_token_value text DEFAULT NULL,
    refresh_token_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    refresh_token_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    refresh_token_metadata text DEFAULT NULL,
    user_code_value text DEFAULT NULL,
    user_code_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    user_code_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    user_code_metadata text DEFAULT NULL,
    device_code_value text DEFAULT NULL,
    device_code_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    device_code_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    device_code_metadata text DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
