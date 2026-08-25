-- PostgreSQL schema baseline at application version 84.
-- Flyway selects this baseline for an empty schema and skips MySQL V1-V84.
--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11 (Ubuntu 17.11-1.pgdg24.04+2)
-- Dumped by pg_dump version 17.11 (Ubuntu 17.11-1.pgdg24.04+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET search_path TO ${flyway:defaultSchema}, public;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: openflash; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: on_update_current_timestamp_pw_async_task(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_async_task() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_card(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_card() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_card_ai_cache(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_card_ai_cache() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_card_progress(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_card_progress() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_deck(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_deck() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_deck_ai_settings(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_deck_ai_settings() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_feature_flag(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_feature_flag() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_mask_mode_deck_settings(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_mask_mode_deck_settings() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_platform_ai_connection(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_platform_ai_connection() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_platform_ai_secret(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_platform_ai_secret() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_practice_session_store(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_practice_session_store() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_system_config(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_system_config() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_type_registry(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_type_registry() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_user(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_user() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_user_ai_config(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_user_ai_config() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_user_feature_flag(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_user_feature_flag() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_user_platform_ai_preference(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_user_platform_ai_preference() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


--
-- Name: on_update_current_timestamp_pw_user_settings(); Type: FUNCTION; Schema: openflash; Owner: -
--

CREATE FUNCTION on_update_current_timestamp_pw_user_settings() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   NEW.updated_at = now();
   RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: pw_async_task; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_async_task (
    id bigint NOT NULL,
    owner_user_id bigint,
    biz_key character varying(191) NOT NULL,
    task_type character varying(64) NOT NULL,
    payload text NOT NULL,
    status character varying(20) NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    max_retry_count integer DEFAULT 3 NOT NULL,
    next_retry_at timestamp with time zone,
    lease_until timestamp with time zone,
    last_error character varying(500),
    priority integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_async_task_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_async_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_async_task_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_async_task_id_seq OWNED BY pw_async_task.id;


--
-- Name: pw_card; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_card (
    id integer NOT NULL,
    deck_id bigint NOT NULL,
    side_a text,
    side_b text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT '0'::smallint NOT NULL
);


--
-- Name: pw_card_ai_cache; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_card_ai_cache (
    id bigint NOT NULL,
    owner_user_id bigint NOT NULL,
    content_fingerprint character(64) NOT NULL,
    prompt_fingerprint character(64) NOT NULL,
    prompt text NOT NULL,
    content text,
    think_used boolean,
    last_accessed_at timestamp with time zone,
    last_generated_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_card_ai_cache_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_card_ai_cache_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_card_ai_cache_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_card_ai_cache_id_seq OWNED BY pw_card_ai_cache.id;


--
-- Name: pw_card_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_card_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_card_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_card_id_seq OWNED BY pw_card.id;


--
-- Name: pw_card_media; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_card_media (
    id bigint NOT NULL,
    card_id integer NOT NULL,
    card_side character varying(10) NOT NULL,
    media_url character varying(500) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_card_media_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_card_media_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_card_media_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_card_media_id_seq OWNED BY pw_card_media.id;


--
-- Name: pw_card_progress; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_card_progress (
    id bigint NOT NULL,
    card_id integer NOT NULL,
    user_id bigint NOT NULL,
    direction character varying(20) NOT NULL,
    state character varying(20) DEFAULT 'new'::character varying NOT NULL,
    step integer,
    stability numeric(10,4) DEFAULT 0.0000 NOT NULL,
    difficulty numeric(10,4) DEFAULT 0.0000 NOT NULL,
    next_review_date date,
    last_review_date date,
    reps integer DEFAULT 0 NOT NULL,
    lapses integer DEFAULT 0 NOT NULL,
    last_rating integer DEFAULT 0 NOT NULL,
    first_learned_date date,
    mastered_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_card_progress_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_card_progress_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_card_progress_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_card_progress_id_seq OWNED BY pw_card_progress.id;


--
-- Name: pw_deck; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_deck (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT '0'::smallint NOT NULL
);


--
-- Name: pw_deck_ai_settings; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_deck_ai_settings (
    id bigint NOT NULL,
    deck_id bigint NOT NULL,
    ai_explanation_prompt_a text,
    ai_explanation_prompt_b text,
    ai_completion_enabled boolean DEFAULT false NOT NULL,
    ai_completion_prompt text,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ai_explanation_enabled_a boolean DEFAULT false NOT NULL,
    ai_explanation_enabled_b boolean DEFAULT false NOT NULL
);


--
-- Name: pw_deck_ai_settings_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_deck_ai_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_deck_ai_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_deck_ai_settings_id_seq OWNED BY pw_deck_ai_settings.id;


--
-- Name: pw_deck_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_deck_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_deck_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_deck_id_seq OWNED BY pw_deck.id;


--
-- Name: pw_deck_settings; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_deck_settings (
    id bigint NOT NULL,
    deck_id bigint NOT NULL,
    new_cards_per_day integer DEFAULT 10 NOT NULL,
    target_retention numeric(5,4) DEFAULT 0.9000 NOT NULL,
    review_load_profile character varying(32) DEFAULT 'standard'::character varying NOT NULL,
    duplicate_side_a_enabled boolean DEFAULT true NOT NULL,
    duplicate_side_b_enabled boolean DEFAULT false NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    tts_language_a character varying(10),
    tts_language_b character varying(10)
);


--
-- Name: pw_deck_settings_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_deck_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_deck_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_deck_settings_id_seq OWNED BY pw_deck_settings.id;


--
-- Name: pw_feature_flag; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_feature_flag (
    id bigint NOT NULL,
    feature_key character varying(191) NOT NULL,
    enabled smallint DEFAULT '1'::smallint NOT NULL,
    rollout_type character varying(20) DEFAULT 'GLOBAL'::character varying NOT NULL,
    description character varying(500),
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(50)
);


--
-- Name: pw_feature_flag_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_feature_flag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_feature_flag_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_feature_flag_id_seq OWNED BY pw_feature_flag.id;


--
-- Name: pw_mask_mode_deck_settings; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_mask_mode_deck_settings (
    deck_id bigint NOT NULL,
    mode character varying(16) DEFAULT 'random'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_platform_ai_connection; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_platform_ai_connection (
    id bigint NOT NULL,
    connection_key character varying(64) NOT NULL,
    kind character varying(16) NOT NULL,
    protocol character varying(40) NOT NULL,
    cli_key character varying(64),
    config json NOT NULL,
    credentials_configured smallint DEFAULT '0'::smallint NOT NULL,
    enabled smallint DEFAULT '1'::smallint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_platform_ai_connection_cli_key CHECK (((((kind)::text = 'API'::text) AND (cli_key IS NULL)) OR (((kind)::text = 'CLI'::text) AND (cli_key IS NOT NULL)))),
    CONSTRAINT chk_platform_ai_connection_kind CHECK (((kind)::text = ANY ((ARRAY['API'::character varying, 'CLI'::character varying])::text[])))
);


--
-- Name: pw_platform_ai_connection_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_platform_ai_connection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_platform_ai_connection_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_platform_ai_connection_id_seq OWNED BY pw_platform_ai_connection.id;


--
-- Name: pw_platform_ai_offering; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_platform_ai_offering (
    id bigint NOT NULL,
    connection_id bigint NOT NULL,
    offering_key character varying(64) NOT NULL,
    model_key character varying(191),
    reasoning_effort character varying(32),
    dynamic_connection_id bigint GENERATED ALWAYS AS (
        CASE WHEN model_key IS NULL THEN connection_id ELSE NULL::bigint END
    ) STORED,
    enabled smallint DEFAULT '1'::smallint NOT NULL,
    default_access smallint DEFAULT '0'::smallint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL
);


--
-- Name: pw_platform_ai_offering_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_platform_ai_offering_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_platform_ai_offering_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_platform_ai_offering_id_seq OWNED BY pw_platform_ai_offering.id;


--
-- Name: pw_platform_ai_secret; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_platform_ai_secret (
    connection_id bigint NOT NULL,
    secret_enc text NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_platform_ai_user_access; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_platform_ai_user_access (
    user_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    enabled smallint NOT NULL
);


--
-- Name: pw_plugin_install; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_plugin_install (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    deck_id bigint NOT NULL,
    plugin_id character varying(64) NOT NULL,
    installed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_plugin_install_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_plugin_install_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_plugin_install_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_plugin_install_id_seq OWNED BY pw_plugin_install.id;


--
-- Name: pw_practice_session_store; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_practice_session_store (
    user_id bigint NOT NULL,
    deck_id bigint NOT NULL,
    data text NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_system_config; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_system_config (
    id bigint NOT NULL,
    group_name character varying(64) NOT NULL,
    config_key character varying(191) NOT NULL,
    value character varying(2000) NOT NULL,
    value_type character varying(20) NOT NULL,
    description character varying(500),
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(50)
);


--
-- Name: pw_system_config_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_system_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_system_config_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_system_config_id_seq OWNED BY pw_system_config.id;


--
-- Name: pw_tts_deck_settings; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_tts_deck_settings (
    deck_id bigint NOT NULL,
    auto_speak_a boolean DEFAULT false NOT NULL,
    auto_speak_b boolean DEFAULT false NOT NULL,
    engine character varying(32) DEFAULT 'cosyvoice3'::character varying NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


--
-- Name: pw_type_registry; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_type_registry (
    id bigint NOT NULL,
    registry_type character varying(64) NOT NULL,
    item_key character varying(191) NOT NULL,
    item_name character varying(200),
    config text,
    sort_order integer DEFAULT 0 NOT NULL,
    enabled smallint DEFAULT '1'::smallint NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_type_registry_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_type_registry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_type_registry_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_type_registry_id_seq OWNED BY pw_type_registry.id;


--
-- Name: pw_user; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_user (
    id bigint NOT NULL,
    username character varying(50) NOT NULL,
    password_hash character varying(255) NOT NULL,
    nickname character varying(50),
    role character varying(16) DEFAULT 'USER'::character varying NOT NULL,
    admin_approved smallint DEFAULT '0'::smallint NOT NULL,
    admin_approved_at timestamp with time zone,
    admin_approval_source character varying(32),
    banned smallint DEFAULT '0'::smallint NOT NULL,
    auth_version bigint DEFAULT '0'::bigint NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT '0'::smallint NOT NULL,
    CONSTRAINT chk_pw_user_admin_approved CHECK ((admin_approved = ANY (ARRAY[0, 1]))),
    CONSTRAINT chk_pw_user_role CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'USER'::character varying])::text[])))
);


--
-- Name: pw_user_active_ai_selection; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_user_active_ai_selection (
    user_id bigint NOT NULL,
    source character varying(16) NOT NULL,
    user_provider_key character varying(64),
    offering_id bigint,
    CONSTRAINT chk_user_active_ai_source CHECK (((((source)::text = 'USER'::text) AND (user_provider_key IS NOT NULL) AND (offering_id IS NULL)) OR (((source)::text = 'PLATFORM'::text) AND (user_provider_key IS NULL) AND (offering_id IS NOT NULL))))
);


--
-- Name: pw_user_ai_config; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_user_ai_config (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    provider character varying(20) NOT NULL,
    config character varying(2000) DEFAULT '{}'::character varying NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_user_ai_config_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_user_ai_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_user_ai_config_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_user_ai_config_id_seq OWNED BY pw_user_ai_config.id;


--
-- Name: pw_user_feature_flag; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_user_feature_flag (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    feature_key character varying(191) NOT NULL,
    enabled smallint NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_user_feature_flag_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_user_feature_flag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_user_feature_flag_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_user_feature_flag_id_seq OWNED BY pw_user_feature_flag.id;


--
-- Name: pw_user_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_user_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_user_id_seq OWNED BY pw_user.id;


--
-- Name: pw_user_platform_ai_preference; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_user_platform_ai_preference (
    user_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    model character varying(191),
    reasoning_effort character varying(32),
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_user_settings; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_user_settings (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    theme character varying(20) DEFAULT 'light'::character varying NOT NULL,
    last_exported_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sound_enabled boolean DEFAULT true NOT NULL,
    language character varying(10) DEFAULT 'en'::character varying NOT NULL
);


--
-- Name: pw_user_settings_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_user_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_user_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_user_settings_id_seq OWNED BY pw_user_settings.id;


--
-- Name: pw_user_upload; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE pw_user_upload (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    relative_path character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pw_user_upload_id_seq; Type: SEQUENCE; Schema: openflash; Owner: -
--

CREATE SEQUENCE pw_user_upload_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pw_user_upload_id_seq; Type: SEQUENCE OWNED BY; Schema: openflash; Owner: -
--

ALTER SEQUENCE pw_user_upload_id_seq OWNED BY pw_user_upload.id;


--
-- Name: spring_session; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100)
);


--
-- Name: spring_session_attributes; Type: TABLE; Schema: openflash; Owner: -
--

CREATE TABLE spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL
);


--
-- Name: pw_async_task id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_async_task ALTER COLUMN id SET DEFAULT nextval('pw_async_task_id_seq'::regclass);


--
-- Name: pw_card id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card ALTER COLUMN id SET DEFAULT nextval('pw_card_id_seq'::regclass);


--
-- Name: pw_card_ai_cache id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_ai_cache ALTER COLUMN id SET DEFAULT nextval('pw_card_ai_cache_id_seq'::regclass);


--
-- Name: pw_card_media id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_media ALTER COLUMN id SET DEFAULT nextval('pw_card_media_id_seq'::regclass);


--
-- Name: pw_card_progress id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_progress ALTER COLUMN id SET DEFAULT nextval('pw_card_progress_id_seq'::regclass);


--
-- Name: pw_deck id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck ALTER COLUMN id SET DEFAULT nextval('pw_deck_id_seq'::regclass);


--
-- Name: pw_deck_ai_settings id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck_ai_settings ALTER COLUMN id SET DEFAULT nextval('pw_deck_ai_settings_id_seq'::regclass);


--
-- Name: pw_deck_settings id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck_settings ALTER COLUMN id SET DEFAULT nextval('pw_deck_settings_id_seq'::regclass);


--
-- Name: pw_feature_flag id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_feature_flag ALTER COLUMN id SET DEFAULT nextval('pw_feature_flag_id_seq'::regclass);


--
-- Name: pw_platform_ai_connection id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_connection ALTER COLUMN id SET DEFAULT nextval('pw_platform_ai_connection_id_seq'::regclass);


--
-- Name: pw_platform_ai_offering id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_offering ALTER COLUMN id SET DEFAULT nextval('pw_platform_ai_offering_id_seq'::regclass);


--
-- Name: pw_plugin_install id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_plugin_install ALTER COLUMN id SET DEFAULT nextval('pw_plugin_install_id_seq'::regclass);


--
-- Name: pw_system_config id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_system_config ALTER COLUMN id SET DEFAULT nextval('pw_system_config_id_seq'::regclass);


--
-- Name: pw_type_registry id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_type_registry ALTER COLUMN id SET DEFAULT nextval('pw_type_registry_id_seq'::regclass);


--
-- Name: pw_user id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user ALTER COLUMN id SET DEFAULT nextval('pw_user_id_seq'::regclass);


--
-- Name: pw_user_ai_config id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_ai_config ALTER COLUMN id SET DEFAULT nextval('pw_user_ai_config_id_seq'::regclass);


--
-- Name: pw_user_feature_flag id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_feature_flag ALTER COLUMN id SET DEFAULT nextval('pw_user_feature_flag_id_seq'::regclass);


--
-- Name: pw_user_settings id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_settings ALTER COLUMN id SET DEFAULT nextval('pw_user_settings_id_seq'::regclass);


--
-- Name: pw_user_upload id; Type: DEFAULT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_upload ALTER COLUMN id SET DEFAULT nextval('pw_user_upload_id_seq'::regclass);


--
-- Name: spring_session idx_16390_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY spring_session
    ADD CONSTRAINT idx_16390_primary PRIMARY KEY (primary_id);


--
-- Name: spring_session_attributes idx_16393_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY spring_session_attributes
    ADD CONSTRAINT idx_16393_primary PRIMARY KEY (session_primary_id, attribute_name);


--
-- Name: pw_async_task idx_16405_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_async_task
    ADD CONSTRAINT idx_16405_primary PRIMARY KEY (id);


--
-- Name: pw_card idx_16416_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card
    ADD CONSTRAINT idx_16416_primary PRIMARY KEY (id);


--
-- Name: pw_card_ai_cache idx_16425_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_ai_cache
    ADD CONSTRAINT idx_16425_primary PRIMARY KEY (id);


--
-- Name: pw_card_media idx_16433_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_media
    ADD CONSTRAINT idx_16433_primary PRIMARY KEY (id);


--
-- Name: pw_card_progress idx_16442_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_progress
    ADD CONSTRAINT idx_16442_primary PRIMARY KEY (id);


--
-- Name: pw_deck idx_16453_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck
    ADD CONSTRAINT idx_16453_primary PRIMARY KEY (id);


--
-- Name: pw_deck_ai_settings idx_16460_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck_ai_settings
    ADD CONSTRAINT idx_16460_primary PRIMARY KEY (id);


--
-- Name: pw_deck_settings idx_16470_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck_settings
    ADD CONSTRAINT idx_16470_primary PRIMARY KEY (id);


--
-- Name: pw_feature_flag idx_16480_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_feature_flag
    ADD CONSTRAINT idx_16480_primary PRIMARY KEY (id);


--
-- Name: pw_mask_mode_deck_settings idx_16488_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_mask_mode_deck_settings
    ADD CONSTRAINT idx_16488_primary PRIMARY KEY (deck_id);


--
-- Name: pw_platform_ai_connection idx_16494_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_connection
    ADD CONSTRAINT idx_16494_primary PRIMARY KEY (id);


--
-- Name: pw_platform_ai_offering idx_16504_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_offering
    ADD CONSTRAINT idx_16504_primary PRIMARY KEY (id);


--
-- Name: pw_platform_ai_secret idx_16511_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_secret
    ADD CONSTRAINT idx_16511_primary PRIMARY KEY (connection_id);


--
-- Name: pw_platform_ai_user_access idx_16516_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_user_access
    ADD CONSTRAINT idx_16516_primary PRIMARY KEY (user_id, offering_id);


--
-- Name: pw_plugin_install idx_16520_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_plugin_install
    ADD CONSTRAINT idx_16520_primary PRIMARY KEY (id);


--
-- Name: pw_practice_session_store idx_16525_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_practice_session_store
    ADD CONSTRAINT idx_16525_primary PRIMARY KEY (user_id, deck_id);


--
-- Name: pw_system_config idx_16531_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_system_config
    ADD CONSTRAINT idx_16531_primary PRIMARY KEY (id);


--
-- Name: pw_tts_deck_settings idx_16537_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_tts_deck_settings
    ADD CONSTRAINT idx_16537_primary PRIMARY KEY (deck_id);


--
-- Name: pw_type_registry idx_16544_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_type_registry
    ADD CONSTRAINT idx_16544_primary PRIMARY KEY (id);


--
-- Name: pw_user idx_16554_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user
    ADD CONSTRAINT idx_16554_primary PRIMARY KEY (id);


--
-- Name: pw_user_active_ai_selection idx_16564_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_active_ai_selection
    ADD CONSTRAINT idx_16564_primary PRIMARY KEY (user_id);


--
-- Name: pw_user_ai_config idx_16568_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_ai_config
    ADD CONSTRAINT idx_16568_primary PRIMARY KEY (id);


--
-- Name: pw_user_feature_flag idx_16576_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_feature_flag
    ADD CONSTRAINT idx_16576_primary PRIMARY KEY (id);


--
-- Name: pw_user_settings idx_16584_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_settings
    ADD CONSTRAINT idx_16584_primary PRIMARY KEY (id);


--
-- Name: pw_user_upload idx_16592_primary; Type: CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_upload
    ADD CONSTRAINT idx_16592_primary PRIMARY KEY (id);


--
-- Name: idx_16390_spring_session_ix1; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16390_spring_session_ix1 ON spring_session USING btree (session_id);


--
-- Name: idx_16390_spring_session_ix2; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16390_spring_session_ix2 ON spring_session USING btree (expiry_time);


--
-- Name: idx_16390_spring_session_ix3; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16390_spring_session_ix3 ON spring_session USING btree (principal_name);


--
-- Name: idx_16405_fk_async_task_owner_user; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16405_fk_async_task_owner_user ON pw_async_task USING btree (owner_user_id);


--
-- Name: idx_16405_idx_pw_async_task_claim; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16405_idx_pw_async_task_claim ON pw_async_task USING btree (status, next_retry_at, lease_until, priority, created_at);


--
-- Name: idx_16405_uk_pw_async_task_biz_key; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16405_uk_pw_async_task_biz_key ON pw_async_task USING btree (biz_key);


--
-- Name: idx_16416_idx_pw_card_deck_id; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16416_idx_pw_card_deck_id ON pw_card USING btree (deck_id);


--
-- Name: idx_16425_idx_pw_card_ai_cache_last_accessed_at; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16425_idx_pw_card_ai_cache_last_accessed_at ON pw_card_ai_cache USING btree (last_accessed_at);


--
-- Name: idx_16425_uk_pw_card_ai_cache_owner_fingerprint; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16425_uk_pw_card_ai_cache_owner_fingerprint ON pw_card_ai_cache USING btree (owner_user_id, content_fingerprint);


--
-- Name: idx_16433_idx_pw_card_media_card_id; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16433_idx_pw_card_media_card_id ON pw_card_media USING btree (card_id);


--
-- Name: idx_16433_idx_pw_card_media_media_url; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16433_idx_pw_card_media_media_url ON pw_card_media USING btree (media_url);


--
-- Name: idx_16442_idx_pw_card_progress_card_id; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16442_idx_pw_card_progress_card_id ON pw_card_progress USING btree (card_id);


--
-- Name: idx_16442_idx_pw_card_progress_next_review_date; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16442_idx_pw_card_progress_next_review_date ON pw_card_progress USING btree (next_review_date);


--
-- Name: idx_16442_idx_pw_card_progress_user_card; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16442_idx_pw_card_progress_user_card ON pw_card_progress USING btree (user_id, card_id);


--
-- Name: idx_16442_idx_pw_card_progress_user_direction_next_review_date; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16442_idx_pw_card_progress_user_direction_next_review_date ON pw_card_progress USING btree (user_id, direction, next_review_date);


--
-- Name: idx_16442_idx_pw_card_progress_user_id; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16442_idx_pw_card_progress_user_id ON pw_card_progress USING btree (user_id);


--
-- Name: idx_16442_idx_pw_card_progress_user_last_review_card_direction; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16442_idx_pw_card_progress_user_last_review_card_direction ON pw_card_progress USING btree (user_id, last_review_date, card_id, direction);


--
-- Name: idx_16442_uk_pw_card_progress_user_card_direction; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16442_uk_pw_card_progress_user_card_direction ON pw_card_progress USING btree (user_id, card_id, direction);


--
-- Name: idx_16453_idx_pw_deck_user_id; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16453_idx_pw_deck_user_id ON pw_deck USING btree (user_id);


--
-- Name: idx_16460_uq_deck_ai_settings_deck_id; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16460_uq_deck_ai_settings_deck_id ON pw_deck_ai_settings USING btree (deck_id);


--
-- Name: idx_16470_uq_deck_settings_deck_id; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16470_uq_deck_settings_deck_id ON pw_deck_settings USING btree (deck_id);


--
-- Name: idx_16480_uk_pw_feature_flag_key; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16480_uk_pw_feature_flag_key ON pw_feature_flag USING btree (feature_key);


--
-- Name: idx_16494_uk_platform_ai_cli_key; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16494_uk_platform_ai_cli_key ON pw_platform_ai_connection USING btree (cli_key);


--
-- Name: idx_16494_uk_platform_ai_connection_key; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16494_uk_platform_ai_connection_key ON pw_platform_ai_connection USING btree (connection_key);


--
-- Name: idx_16504_fk_platform_ai_offering_connection; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16504_fk_platform_ai_offering_connection ON pw_platform_ai_offering USING btree (connection_id);


--
-- Name: idx_16504_uk_platform_ai_dynamic_connection; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16504_uk_platform_ai_dynamic_connection ON pw_platform_ai_offering USING btree (dynamic_connection_id);


--
-- Name: idx_16504_uk_platform_ai_offering_key; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16504_uk_platform_ai_offering_key ON pw_platform_ai_offering USING btree (offering_key);


--
-- Name: idx_16516_fk_platform_ai_access_offering; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16516_fk_platform_ai_access_offering ON pw_platform_ai_user_access USING btree (offering_id);


--
-- Name: idx_16520_idx_plugin_install_deck; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16520_idx_plugin_install_deck ON pw_plugin_install USING btree (deck_id);


--
-- Name: idx_16520_idx_plugin_install_user; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16520_idx_plugin_install_user ON pw_plugin_install USING btree (user_id);


--
-- Name: idx_16520_uk_plugin_install; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16520_uk_plugin_install ON pw_plugin_install USING btree (user_id, deck_id, plugin_id);


--
-- Name: idx_16525_fk_practice_deck; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16525_fk_practice_deck ON pw_practice_session_store USING btree (deck_id);


--
-- Name: idx_16531_uk_pw_system_config_key; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16531_uk_pw_system_config_key ON pw_system_config USING btree (config_key);


--
-- Name: idx_16544_uk_pw_type_registry; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16544_uk_pw_type_registry ON pw_type_registry USING btree (registry_type, item_key);


--
-- Name: idx_16554_idx_pw_user_active_admin; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16554_idx_pw_user_active_admin ON pw_user USING btree (deleted, banned, role);


--
-- Name: idx_16554_uk_pw_user_username; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16554_uk_pw_user_username ON pw_user USING btree (username);


--
-- Name: idx_16564_fk_user_active_ai_offering; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16564_fk_user_active_ai_offering ON pw_user_active_ai_selection USING btree (offering_id);


--
-- Name: idx_16568_uq_user_provider; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16568_uq_user_provider ON pw_user_ai_config USING btree (user_id, provider);


--
-- Name: idx_16576_uk_pw_user_feature_flag; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16576_uk_pw_user_feature_flag ON pw_user_feature_flag USING btree (user_id, feature_key);


--
-- Name: idx_16580_fk_user_platform_preference_offering; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16580_fk_user_platform_preference_offering ON pw_user_platform_ai_preference USING btree (offering_id);


--
-- Name: idx_16580_uk_user_platform_preference; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16580_uk_user_platform_preference ON pw_user_platform_ai_preference USING btree (user_id, offering_id);


--
-- Name: idx_16584_uk_pw_user_settings_user_id; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16584_uk_pw_user_settings_user_id ON pw_user_settings USING btree (user_id);


--
-- Name: idx_16592_idx_pw_user_upload_user; Type: INDEX; Schema: openflash; Owner: -
--

CREATE INDEX idx_16592_idx_pw_user_upload_user ON pw_user_upload USING btree (user_id);


--
-- Name: idx_16592_uk_pw_user_upload_path; Type: INDEX; Schema: openflash; Owner: -
--

CREATE UNIQUE INDEX idx_16592_uk_pw_user_upload_path ON pw_user_upload USING btree (relative_path);


--
-- Name: pw_async_task on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_async_task FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_async_task();


--
-- Name: pw_card on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_card FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_card();


--
-- Name: pw_card_ai_cache on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_card_ai_cache FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_card_ai_cache();


--
-- Name: pw_card_progress on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_card_progress FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_card_progress();


--
-- Name: pw_deck on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_deck FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_deck();


--
-- Name: pw_deck_ai_settings on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_deck_ai_settings FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_deck_ai_settings();


--
-- Name: pw_feature_flag on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_feature_flag FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_feature_flag();


--
-- Name: pw_mask_mode_deck_settings on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_mask_mode_deck_settings FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_mask_mode_deck_settings();


--
-- Name: pw_platform_ai_connection on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_platform_ai_connection FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_platform_ai_connection();


--
-- Name: pw_platform_ai_secret on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_platform_ai_secret FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_platform_ai_secret();


--
-- Name: pw_practice_session_store on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_practice_session_store FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_practice_session_store();


--
-- Name: pw_system_config on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_system_config FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_system_config();


--
-- Name: pw_type_registry on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_type_registry FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_type_registry();


--
-- Name: pw_user on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_user FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_user();


--
-- Name: pw_user_ai_config on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_user_ai_config FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_user_ai_config();


--
-- Name: pw_user_feature_flag on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_user_feature_flag FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_user_feature_flag();


--
-- Name: pw_user_platform_ai_preference on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_user_platform_ai_preference FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_user_platform_ai_preference();


--
-- Name: pw_user_settings on_update_current_timestamp; Type: TRIGGER; Schema: openflash; Owner: -
--

CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON pw_user_settings FOR EACH ROW EXECUTE FUNCTION on_update_current_timestamp_pw_user_settings();


--
-- Name: pw_async_task fk_async_task_owner_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_async_task
    ADD CONSTRAINT fk_async_task_owner_user FOREIGN KEY (owner_user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_card fk_card_deck; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card
    ADD CONSTRAINT fk_card_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;


--
-- Name: pw_card_media fk_card_media_card; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_media
    ADD CONSTRAINT fk_card_media_card FOREIGN KEY (card_id) REFERENCES pw_card(id) ON DELETE CASCADE;


--
-- Name: pw_card_progress fk_card_progress_card; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_progress
    ADD CONSTRAINT fk_card_progress_card FOREIGN KEY (card_id) REFERENCES pw_card(id) ON DELETE CASCADE;


--
-- Name: pw_card_progress fk_card_progress_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_progress
    ADD CONSTRAINT fk_card_progress_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_deck_ai_settings fk_deck_ai_settings_deck; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck_ai_settings
    ADD CONSTRAINT fk_deck_ai_settings_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;


--
-- Name: pw_deck_settings fk_deck_settings_deck; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck_settings
    ADD CONSTRAINT fk_deck_settings_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;


--
-- Name: pw_deck fk_deck_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_deck
    ADD CONSTRAINT fk_deck_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_mask_mode_deck_settings fk_mask_mode_deck_settings_deck; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_mask_mode_deck_settings
    ADD CONSTRAINT fk_mask_mode_deck_settings_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;


--
-- Name: pw_platform_ai_user_access fk_platform_ai_access_offering; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_user_access
    ADD CONSTRAINT fk_platform_ai_access_offering FOREIGN KEY (offering_id) REFERENCES pw_platform_ai_offering(id) ON DELETE CASCADE;


--
-- Name: pw_platform_ai_user_access fk_platform_ai_access_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_user_access
    ADD CONSTRAINT fk_platform_ai_access_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_platform_ai_offering fk_platform_ai_offering_connection; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_offering
    ADD CONSTRAINT fk_platform_ai_offering_connection FOREIGN KEY (connection_id) REFERENCES pw_platform_ai_connection(id) ON DELETE CASCADE;


--
-- Name: pw_platform_ai_secret fk_platform_ai_secret_connection; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_platform_ai_secret
    ADD CONSTRAINT fk_platform_ai_secret_connection FOREIGN KEY (connection_id) REFERENCES pw_platform_ai_connection(id) ON DELETE CASCADE;


--
-- Name: pw_plugin_install fk_plugin_install_deck; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_plugin_install
    ADD CONSTRAINT fk_plugin_install_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;


--
-- Name: pw_plugin_install fk_plugin_install_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_plugin_install
    ADD CONSTRAINT fk_plugin_install_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_practice_session_store fk_practice_deck; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_practice_session_store
    ADD CONSTRAINT fk_practice_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;


--
-- Name: pw_practice_session_store fk_practice_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_practice_session_store
    ADD CONSTRAINT fk_practice_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_card_ai_cache fk_pw_card_ai_cache_owner; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_card_ai_cache
    ADD CONSTRAINT fk_pw_card_ai_cache_owner FOREIGN KEY (owner_user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_tts_deck_settings fk_tts_deck_settings_deck; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_tts_deck_settings
    ADD CONSTRAINT fk_tts_deck_settings_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;


--
-- Name: pw_user_active_ai_selection fk_user_active_ai_offering; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_active_ai_selection
    ADD CONSTRAINT fk_user_active_ai_offering FOREIGN KEY (offering_id) REFERENCES pw_platform_ai_offering(id) ON DELETE CASCADE;


--
-- Name: pw_user_active_ai_selection fk_user_active_ai_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_active_ai_selection
    ADD CONSTRAINT fk_user_active_ai_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_user_ai_config fk_user_ai_config_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_ai_config
    ADD CONSTRAINT fk_user_ai_config_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_user_feature_flag fk_user_feature_flag_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_feature_flag
    ADD CONSTRAINT fk_user_feature_flag_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_user_platform_ai_preference fk_user_platform_preference_offering; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_platform_ai_preference
    ADD CONSTRAINT fk_user_platform_preference_offering FOREIGN KEY (offering_id) REFERENCES pw_platform_ai_offering(id) ON DELETE CASCADE;


--
-- Name: pw_user_platform_ai_preference fk_user_platform_preference_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_platform_ai_preference
    ADD CONSTRAINT fk_user_platform_preference_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_user_settings fk_user_settings_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_settings
    ADD CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: pw_user_upload fk_user_upload_user; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY pw_user_upload
    ADD CONSTRAINT fk_user_upload_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;


--
-- Name: spring_session_attributes spring_session_attributes_fk; Type: FK CONSTRAINT; Schema: openflash; Owner: -
--

ALTER TABLE ONLY spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES spring_session(primary_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--
