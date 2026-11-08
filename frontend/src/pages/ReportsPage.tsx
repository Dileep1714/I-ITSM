import { BarChart, LineChart } from '@mantine/charts';
import {
  Anchor,
  Badge,
  Card,
  Group,
  Paper,
  Progress,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Table,
  Tabs,
  Text,
  Title,
} from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { OverviewReport } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PriorityBadge } from '../components/Badges';
import { QueryState } from '../components/QueryState';
import { formatMinutes, humanize } from '../lib/format';

const RANGES = [
  { value: '7', label: '7 days' },
  { value: '30', label: '30 days' },
  { value: '90', label: '90 days' },
];

/** A single headline number: no chart needed. */
function StatTile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Paper withBorder p="md" radius="md">
      <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
        {label}
      </Text>
      <Text fw={700} fz={26}>
        {value}
      </Text>
      {hint && (
        <Text size="xs" c="dimmed">
          {hint}
        </Text>
      )}
    </Paper>
  );
}

export function ReportsPage() {
  const { user } = useAuth();
  const [days, setDays] = useState('30');
  const to = dayjs().format('YYYY-MM-DD');
  const from = dayjs().subtract(Number(days) - 1, 'day').format('YYYY-MM-DD');

  const overview = useQuery({ queryKey: ['reports', 'overview', from, to], queryFn: () => api.reports.overview(from, to) });
  const workload = useQuery({ queryKey: ['reports', 'technicians', from, to], queryFn: () => api.reports.technicians(from, to) });
  const services = useQuery({ queryKey: ['reports', 'services', from, to], queryFn: () => api.reports.services(from, to) });

  return (
    <Stack>
      <Group justify="space-between" align="flex-end">
        <div>
          <Title order={2}>Reports</Title>
          <Text size="sm" c="dimmed">
            Incidents reported between {dayjs(from).format('D MMM')} and {dayjs(to).format('D MMM YYYY')} (UTC days).
          </Text>
        </div>
        <SegmentedControl data={RANGES} value={days} onChange={setDays} />
      </Group>

      <Tabs defaultValue="overview" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="overview">Incident overview</Tabs.Tab>
          <Tabs.Tab value="technicians">{user?.role === 'ADMINISTRATOR' ? 'Technician workload' : 'Workload'}</Tabs.Tab>
          <Tabs.Tab value="services">Service analysis</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="overview">
          <QueryState query={overview}>{overview.data && <Overview report={overview.data} />}</QueryState>
        </Tabs.Panel>

        <Tabs.Panel value="technicians">
          <QueryState query={workload}>
            {workload.data && (
              <Paper withBorder radius="md" p="md">
                <Table.ScrollContainer minWidth={820}>
                  <Table striped highlightOnHover verticalSpacing="xs">
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>Technician</Table.Th>
                        <Table.Th>Level</Table.Th>
                        <Table.Th>Availability</Table.Th>
                        <Table.Th w={180}>Current load</Table.Th>
                        <Table.Th ta="right">Assigned</Table.Th>
                        <Table.Th ta="right">Resolved</Table.Th>
                        <Table.Th ta="right">Avg. resolution</Table.Th>
                        <Table.Th ta="right">SLA breaches</Table.Th>
                        <Table.Th ta="right">Satisfaction</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {workload.data.technicians.map((t) => (
                        <Table.Tr key={t.technicianId} opacity={t.active ? 1 : 0.5}>
                          <Table.Td fw={500}>{t.fullName}</Table.Td>
                          <Table.Td>{humanize(t.supportLevel.replace(/^L\d_/, ''))}</Table.Td>
                          <Table.Td>
                            <Badge size="sm" variant="light" color={t.availability === 'AVAILABLE' ? 'green' : 'gray'}>
                              {humanize(t.availability)}
                            </Badge>
                          </Table.Td>
                          <Table.Td>
                            <Group gap="xs" wrap="nowrap">
                              <Progress
                                value={Math.min(100, t.utilizationPercent)}
                                color={t.utilizationPercent >= 100 ? 'red' : t.utilizationPercent >= 75 ? 'orange' : 'teal'}
                                style={{ flex: 1 }}
                                aria-label={`${t.utilizationPercent}% of capacity`}
                              />
                              <Text size="xs" c="dimmed" ff="monospace">
                                {t.activeIncidents}/{t.maxActiveIncidents}
                              </Text>
                            </Group>
                          </Table.Td>
                          <Table.Td ta="right">{t.assignedInPeriod}</Table.Td>
                          <Table.Td ta="right">{t.resolvedInPeriod}</Table.Td>
                          <Table.Td ta="right">{formatMinutes(t.avgResolutionMinutes)}</Table.Td>
                          <Table.Td ta="right" c={t.slaBreaches > 0 ? 'red' : undefined}>
                            {t.slaBreaches}
                          </Table.Td>
                          <Table.Td ta="right">{t.avgSatisfaction !== null ? `${t.avgSatisfaction} / 5` : '—'}</Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
                {user?.role === 'TECHNICIAN' && workload.data.technicians.length === 1 && (
                  <Text size="xs" c="dimmed" mt="sm">
                    Team leads and administrators see the whole team.
                  </Text>
                )}
              </Paper>
            )}
          </QueryState>
        </Tabs.Panel>

        <Tabs.Panel value="services">
          <QueryState query={services}>
            {services.data && (
              <Paper withBorder radius="md" p="md">
                <Table.ScrollContainer minWidth={820}>
                  <Table striped highlightOnHover verticalSpacing="xs">
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>Service</Table.Th>
                        <Table.Th>Criticality</Table.Th>
                        <Table.Th>Status</Table.Th>
                        <Table.Th ta="right">Incidents</Table.Th>
                        <Table.Th ta="right">Open</Table.Th>
                        <Table.Th ta="right">P1/P2</Table.Th>
                        <Table.Th ta="right">SLA breaches</Table.Th>
                        <Table.Th ta="right">Avg. resolution</Table.Th>
                        <Table.Th>Top category</Table.Th>
                        <Table.Th>Depends on</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {services.data.services.map((s) => (
                        <Table.Tr key={s.serviceId}>
                          <Table.Td fw={500}>
                            <Anchor component={Link} to={`/incidents?scope=ALL&serviceId=${s.serviceId}`} size="sm">
                              {s.name}
                            </Anchor>
                          </Table.Td>
                          <Table.Td>{humanize(s.criticality)}</Table.Td>
                          <Table.Td>
                            <Badge size="sm" variant="light" color={s.status === 'OPERATIONAL' ? 'green' : 'orange'}>
                              {humanize(s.status)}
                            </Badge>
                          </Table.Td>
                          <Table.Td ta="right">{s.total}</Table.Td>
                          <Table.Td ta="right">{s.open}</Table.Td>
                          <Table.Td ta="right">{s.highPriority}</Table.Td>
                          <Table.Td ta="right" c={s.slaBreaches > 0 ? 'red' : undefined}>
                            {s.slaBreaches}
                          </Table.Td>
                          <Table.Td ta="right">{formatMinutes(s.avgResolutionMinutes)}</Table.Td>
                          <Table.Td>{s.topCategory ?? '—'}</Table.Td>
                          <Table.Td>
                            <Text size="xs" c="dimmed">
                              {s.dependsOn.join(', ') || '—'}
                            </Text>
                          </Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Table.ScrollContainer>
              </Paper>
            )}
          </QueryState>
        </Tabs.Panel>
      </Tabs>
    </Stack>
  );
}

function Overview({ report }: { report: OverviewReport }) {
  const t = report.totals;
  const [showTable, setShowTable] = useState(false);
  const daily = report.daily.map((d) => ({ ...d, day: dayjs(d.date).format('D MMM') }));
  const priorities = report.byPriority.map((p) => ({ priority: p.priority, Incidents: p.count }));
  const categories = report.byCategory.slice(0, 8).map((c) => ({ category: c.key, Incidents: c.count }));

  return (
    <Stack>
      <SimpleGrid cols={{ base: 2, sm: 3, lg: 6 }}>
        <StatTile label="Reported" value={String(t.created)} hint={`${t.openNow} still open`} />
        <StatTile label="Resolved" value={String(t.resolved)} hint={`${t.closed} closed · ${t.cancelled} cancelled`} />
        <StatTile
          label="SLA compliance"
          value={t.slaCompliancePercent !== null ? `${t.slaCompliancePercent}%` : '—'}
          hint={`${t.slaBreached} breached`}
        />
        <StatTile label="Avg. response" value={formatMinutes(t.avgResponseMinutes)} hint="classified → acknowledged" />
        <StatTile label="Avg. resolution" value={formatMinutes(t.avgResolutionMinutes)} hint="reported → resolved" />
        <StatTile
          label="Satisfaction"
          value={t.avgSatisfaction !== null ? `${t.avgSatisfaction} / 5` : '—'}
          hint={`${t.feedbackCount} rating${t.feedbackCount === 1 ? '' : 's'}`}
        />
      </SimpleGrid>

      <Card withBorder radius="md">
        <Group justify="space-between" mb="sm">
          <Text fw={600}>Reported vs resolved per day</Text>
          <Anchor component="button" size="xs" onClick={() => setShowTable((v) => !v)}>
            {showTable ? 'Show chart' : 'Show as table'}
          </Anchor>
        </Group>
        {showTable ? (
          <Table.ScrollContainer minWidth={300} mah={260}>
            <Table striped verticalSpacing={4}>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Day</Table.Th>
                  <Table.Th ta="right">Reported</Table.Th>
                  <Table.Th ta="right">Resolved</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {daily.map((d) => (
                  <Table.Tr key={d.date}>
                    <Table.Td>{d.day}</Table.Td>
                    <Table.Td ta="right">{d.created}</Table.Td>
                    <Table.Td ta="right">{d.resolved}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        ) : (
          <LineChart
            h={240}
            data={daily}
            dataKey="day"
            series={[
              { name: 'created', label: 'Reported', color: 'var(--viz-series-1)' },
              { name: 'resolved', label: 'Resolved', color: 'var(--viz-series-2)' },
            ]}
            curveType="linear"
            strokeWidth={2}
            withDots={false}
            withLegend
            legendProps={{ verticalAlign: 'top', height: 32 }}
            gridAxis="y"
            gridColor="var(--viz-grid)"
            yAxisProps={{ allowDecimals: false, width: 32 }}
            xAxisProps={{ minTickGap: 24 }}
            activeDotProps={{ r: 4, strokeWidth: 2 }}
          />
        )}
      </Card>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <Card withBorder radius="md">
          <Text fw={600} mb="sm">
            Incidents by priority
          </Text>
          <BarChart
            h={200}
            data={priorities}
            dataKey="priority"
            series={[{ name: 'Incidents', color: 'var(--viz-series-1)' }]}
            barProps={{ radius: [4, 4, 0, 0] }}
            maxBarWidth={40}
            gridAxis="y"
            gridColor="var(--viz-grid)"
            yAxisProps={{ allowDecimals: false, width: 32 }}
          />
          <Table mt="sm" verticalSpacing={4} fz="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Priority</Table.Th>
                <Table.Th ta="right">Total</Table.Th>
                <Table.Th ta="right">Open</Table.Th>
                <Table.Th ta="right">Breached</Table.Th>
                <Table.Th ta="right">Avg. resolution</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {report.byPriority.map((p) => (
                <Table.Tr key={p.priority}>
                  <Table.Td>
                    <PriorityBadge priority={p.priority} />
                  </Table.Td>
                  <Table.Td ta="right">{p.count}</Table.Td>
                  <Table.Td ta="right">{p.open}</Table.Td>
                  <Table.Td ta="right">{p.breached}</Table.Td>
                  <Table.Td ta="right">{formatMinutes(p.avgResolutionMinutes)}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Card>

        <Card withBorder radius="md">
          <Text fw={600} mb="sm">
            Incidents by category
          </Text>
          {categories.length === 0 ? (
            <Text size="sm" c="dimmed">
              No incidents in this period.
            </Text>
          ) : (
            <BarChart
              h={Math.max(160, categories.length * 34)}
              data={categories}
              dataKey="category"
              orientation="vertical"
              series={[{ name: 'Incidents', color: 'var(--viz-series-1)' }]}
              barProps={{ radius: [0, 4, 4, 0] }}
              gridAxis="x"
              gridColor="var(--viz-grid)"
              withBarValueLabel
              xAxisProps={{ allowDecimals: false }}
              yAxisProps={{ width: 110 }}
            />
          )}
          <Group gap="xs" mt="sm">
            {report.byStatus.map((s) => (
              <Badge key={s.key} variant="default" size="sm">
                {humanize(s.key)}: {s.count}
              </Badge>
            ))}
          </Group>
        </Card>
      </SimpleGrid>
    </Stack>
  );
}
