import { Alert, Center, Loader } from '@mantine/core';
import { IconAlertCircle } from '@tabler/icons-react';
import type { UseQueryResult } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { errorMessage } from '../lib/format';

/** Shows a loader or an error for a query, and the children once data is available. */
export function QueryState({ query, children }: { query: UseQueryResult<unknown>; children: ReactNode }) {
  if (query.isPending) {
    return (
      <Center py="xl">
        <Loader />
      </Center>
    );
  }
  if (query.isError) {
    return (
      <Alert color="red" icon={<IconAlertCircle size={16} />} m="sm">
        {errorMessage(query.error)}
      </Alert>
    );
  }
  return <>{children}</>;
}
