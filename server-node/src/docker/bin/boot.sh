#!/bin/sh

require_env_var() {
  if [ "$1" == "" ]; then
    echo "Error: '$2' was not set."
    echo "Aborting."
    exit 1
  fi
}

echo "Convergence Server Node Container starting up.  Checking required environment variables."

# ensure the following environment variables are set. exit script and container if not set.

require_env_var "$orient_db_uri" "orient_db_uri"
require_env_var "$orient_rest_uri" "orient_rest_uri"
require_env_var "$orient_admin_user" "orient_admin_user"
require_env_var "$orient_admin_pass" "orient_admin_pass"

require_env_var "$orient_conv_user" "orient_conv_user"
require_env_var "$orient_conv_pass" "orient_conv_pass"

require_env_var "$admin_rest_user" "admin_rest_user"
require_env_var "$admin_rest_password" "admin_rest_password"

require_env_var "$admin_ui_url" "admin_ui_url"
require_env_var "$registration_base_url" "registration_base_url"
require_env_var "$randomize_db_credentials" "randomize_db_credentials"

echo "All required variables are set.  Booting."
echo ""

/usr/local/bin/confd -onetime -backend env

exec /opt/convergence/bin/server-node