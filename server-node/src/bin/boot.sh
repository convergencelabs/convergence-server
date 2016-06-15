#!/bin/sh

require_env_var() {
  if [ "$1" == "" ]; then
    echo "Error: '$2' was not set."
    echo "Aborting."
    exit 1
  fi
}

echo "Convergence UI Container starting up.  Checking required environment variables."

# ensure the following environment variables are set. exit script and container if not set.

require_env_var "$orient_db_uri" "orient_db_uri"
require_env_var "$orient_rest_uri" "orient_rest_uri"
require_env_var "$orient_admin_user" "orient_admin_user"
require_env_var "$orient_admin_pass" "orient_admin_pass"

require_env_var "$orient_conv_user" "orient_conv_user"
require_env_var "$orient_conv_pass" "orient_conv_pass"

require_env_var "$admin_ui_uri" "admin_ui_uri"
require_env_var "$rest_public_endpoint" "rest_public_endpoint"

echo "All required variables are set.  Booting."
echo ""

/usr/local/bin/confd -onetime -backend env

exec /opt/convergence/bin/server-node