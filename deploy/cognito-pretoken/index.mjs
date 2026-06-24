/**
 * Cognito Pre Token Generation (V2_0) for OrderMS.
 *
 * Adds to each access token:
 * - cognito:groups from the user's pool groups
 * - orderms/read | orderms/write | orderms/admin scopes based on group
 *
 * Group mapping matches CognitoGroupAuthorities.java in OrderMS.
 */
const SCOPE_READ = 'orderms/read';
const SCOPE_WRITE = 'orderms/write';
const SCOPE_ADMIN = 'orderms/admin';

function scopesForGroups(groups) {
  const g = new Set(groups ?? []);
  if (g.has('ADMIN')) {
    return [SCOPE_READ, SCOPE_WRITE, SCOPE_ADMIN];
  }
  if (g.has('SALES')) {
    return [SCOPE_READ, SCOPE_WRITE];
  }
  if (g.has('CUSTOMER')) {
    return [SCOPE_READ];
  }
  return [];
}

export const handler = async (event) => {
  const groups = event.request?.groupConfiguration?.groupsToOverride ?? [];

  event.response = {
    claimsAndScopeOverrideDetails: {
      accessTokenGeneration: {
        claimsToAddOrOverride: {
          'cognito:groups': groups,
        },
        scopesToAdd: scopesForGroups(groups),
        scopesToSuppress: [],
      },
      idTokenGeneration: {
        claimsToAddOrOverride: {
          'cognito:groups': groups,
        },
      },
    },
  };

  return event;
};
