Feature: Database tools (MCP)
  The DatabaseTools service routes calls to the right provider (Postgres or u-code)
  based on the supplied ConnectionParams, and exposes the same CRUD surface for both.

  # ---------------- Postgres path (H2-backed in tests) ----------------

  Scenario: Listing tables on Postgres returns user-created tables
    Given a Postgres connection is configured
    And a Postgres table "users" exists with columns:
      | name | type         | primary_key |
      | id   | INT          | true        |
      | name | VARCHAR(100) | false       |
    When I list tables
    Then the table list contains "users"

  Scenario: Describing a Postgres table reports its primary key
    Given a Postgres connection is configured
    And a Postgres table "products" exists with columns:
      | name  | type        | primary_key |
      | sku   | VARCHAR(20) | true        |
      | price | INT         | false       |
    When I describe table "products"
    Then the schema reports column "sku" as primary key

  Scenario: Insert -> select round-trip on Postgres
    Given a Postgres connection is configured
    And a Postgres table "people" exists with columns:
      | name | type         | primary_key |
      | id   | INT          | true        |
      | name | VARCHAR(50)  | false       |
    When I insert into "people":
      | id   | 1     |
      | name | Alice |
    Then the inserted row has "name" = "Alice"
    When I select from "people" where:
      | id | 1 |
    Then the result row count is 1

  Scenario: Update affects matching rows on Postgres
    Given a Postgres connection is configured
    And a Postgres table "people" exists with columns:
      | name | type         | primary_key |
      | id   | INT          | true        |
      | name | VARCHAR(50)  | false       |
    When I insert into "people":
      | id   | 1   |
      | name | Bob |
    And I update "people" set name to "Robert" where name is "Bob"
    Then the affected row count is 1

  Scenario: Delete with no filter is rejected on Postgres
    Given a Postgres connection is configured
    And a Postgres table "people" exists with columns:
      | name | type         | primary_key |
      | id   | INT          | true        |
      | name | VARCHAR(50)  | false       |
    When I delete from "people" with no filter
    Then the operation should fail with "non-empty where"

  # ---------------- Routing / validation ----------------

  Scenario: Empty connection params are rejected
    Given an empty connection is configured
    When I list tables
    Then the operation should fail with "Connection params invalid"

  # ---------------- u-code path (MockWebServer) ----------------

  Scenario: Selecting items from u-code parses the response envelope
    Given a u-code connection is configured with appId "key-1"
    And the u-code mock will respond with body:
      """
      {"data":{"data":{"response":[{"guid":"g1","name":"A"},{"guid":"g2","name":"B"}]}}}
      """
    When I select all from "people"
    Then the result row count is 2
    And the u-code mock received a "GET" request to path containing "/v2/items/people"

  Scenario: Listing tables on u-code is unsupported
    Given a u-code connection is configured with appId "key-1"
    When I list tables
    Then the operation should fail with "list-tables endpoint"

  Scenario: u-code delete without guid is rejected
    Given a u-code connection is configured with appId "key-1"
    When I delete from "people" where name is "X"
    Then the operation should fail with "guid"

  Scenario: u-code delete by guid hits the item-id URL
    Given a u-code connection is configured with appId "key-1"
    And the u-code mock will respond with body:
      """
      {"status":"done"}
      """
    When I delete u-code item "g-42" from "people"
    Then the u-code mock received a "DELETE" request to path containing "/v2/items/people/g-42"
