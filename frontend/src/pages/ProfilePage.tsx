import { Button, Stack, Text, Title } from '@mantine/core';
import { IconBeach, IconCheck, IconCircleCheck, IconClockPause, IconMoon, type Icon } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Availability, IncidentSearch, IncidentStatus, Role, SupportLevel } from '../api/types';
import { AsciiName } from '../components/AsciiName';
import { QueryState } from '../components/QueryState';
import { errorMessage, formatDateTime, humanize, OPEN_STATUSES } from '../lib/format';
import classes from './ProfilePage.module.css';

const AVAILABILITY: Availability[] = ['AVAILABLE', 'BUSY', 'OFF_DUTY', 'ON_LEAVE'];

const AVAILABILITY_ICON: Record<Availability, Icon> = {
  AVAILABLE: IconCircleCheck,
  BUSY: IconClockPause,
  OFF_DUTY: IconMoon,
  ON_LEAVE: IconBeach,
};

const ROLES: { role: Role; name: string; text: string }[] = [
  {
    role: 'EMPLOYEE',
    name: 'Employee',
    text: 'Reports incidents, answers technicians’ questions and confirms when a fix really works.',
  },
  {
    role: 'TECHNICIAN',
    name: 'Technician',
    text: 'Works the queue by priority: acknowledges, investigates and resolves. Team leads and above assign work.',
  },
  {
    role: 'ADMINISTRATOR',
    name: 'Administrator',
    text: 'Runs the desk: users, catalogue, SLA policies, assignment strategies and the audit log.',
  },
];

const LADDER: { level: SupportLevel; short: string; name: string; text: string }[] = [
  { level: 'L1_TECHNICIAN', short: 'L1', name: 'Technician', text: 'First response on assigned incidents' },
  { level: 'L2_TEAM_LEAD', short: 'L2', name: 'Team lead', text: 'Assigns work; first escalation' },
  { level: 'L3_SYSTEM_ADMIN', short: 'L3', name: 'System admin', text: 'Deep fixes; second escalation' },
  { level: 'L4_IT_MANAGER', short: 'L4', name: 'IT manager', text: 'Final escalation' },
];

const DONE: IncidentStatus[] = ['RESOLVED', 'VERIFIED', 'CLOSED'];

function initials(name: string) {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

/** Counts one search on the server (only the total is used). */
function useCount(filters: IncidentSearch) {
  return useQuery({
    queryKey: ['incidents', 'count', filters],
    queryFn: () => api.incidents.search(filters, 0, 1),
    select: (page) => page.totalElements,
  }).data;
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className={classes.field}>
      <div className={classes.label}>{label}</div>
      <div className={classes.fieldValue}>{value}</div>
    </div>
  );
}

/**
 * The logged-in user's record card: who they are, which role (and accent colour) they hold, their incident
 * record, and for technicians their place on the escalation ladder, workload slots, skills and availability.
 */
export function ProfilePage() {
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ['me', 'profile'], queryFn: api.me.profile });
  const counts = {
    open: useCount({ status: OPEN_STATUSES }),
    done: useCount({ status: DONE }),
    total: useCount({}),
  };
  const setAvailability = useMutation({
    mutationFn: api.me.setAvailability,
    onSuccess: (updated) => {
      queryClient.setQueryData(['me', 'profile'], updated);
      notifications.show({
        color: 'carbon',
        message:
          updated.availability === 'AVAILABLE'
            ? 'You will receive new assignments'
            : `Marked ${humanize(updated.availability)}: no new automatic assignments`,
      });
    },
    onError: (e) => notifications.show({ color: 'stamp', message: errorMessage(e) }),
  });

  return (
    <Stack maw={1100} gap="xl">
      <Title order={2}>My profile</Title>
      <QueryState query={profile}>
        {profile.data &&
          (() => {
            const p = profile.data;
            const isTech = p.role === 'TECHNICIAN';
            const recordNoun = p.role === 'EMPLOYEE' ? 'reported by me' : p.role === 'TECHNICIAN' ? 'assigned to me' : 'across the desk';
            const levelIndex = LADDER.findIndex((l) => l.level === p.supportLevel);
            const role = ROLES.find((r) => r.role === p.role);
            const max = p.maxActiveIncidents ?? 0;
            const active = p.activeIncidents ?? 0;
            const skills = [...p.skills].sort((a, b) => b.proficiency - a.proficiency);
            return (
              <>
                {/* Record card */}
                <section className={classes.card} aria-label="Profile">
                  <div className={classes.photo} aria-hidden="true">
                    {initials(p.fullName)}
                  </div>
                  <div className={classes.cardBody}>
                    <div className={classes.nameRow}>
                      <div className={classes.nameBlock}>
                        <AsciiName name={p.fullName} as="div" />
                        <div className={classes.email}>{p.email}</div>
                      </div>
                    </div>
                    <div className={classes.fields}>
                      <Field label="Role" value={humanize(p.role)} />
                      {isTech && levelIndex >= 0 && (
                        <Field label="Level" value={`${LADDER[levelIndex].short} · ${LADDER[levelIndex].name}`} />
                      )}
                      {p.department && <Field label="Department" value={p.department} />}
                      {p.location && <Field label="Location" value={p.location} />}
                      {isTech && p.experienceYears !== null && (
                        <Field label="Experience" value={`${p.experienceYears} ${p.experienceYears === 1 ? 'year' : 'years'}`} />
                      )}
                      <Field label="Member since" value={formatDateTime(p.createdAt)} />
                      <Field label="Account" value={p.active ? 'Active' : 'Deactivated'} />
                    </div>
                  </div>
                </section>

                {/* Your role */}
                {role && (
                  <section>
                    <div className={classes.sectionTitle}>Your role</div>
                    <div className={classes.role} data-role={role.role} data-mine>
                      <span className={classes.roleName}>
                        <span className={classes.swatch} aria-hidden="true" />
                        {role.name}
                        {isTech && levelIndex >= 0 && (
                          <span className={classes.roleLevel}>
                            {LADDER[levelIndex].short} · {LADDER[levelIndex].name}
                          </span>
                        )}
                      </span>
                      <span className={classes.roleText}>{role.text}</span>
                    </div>
                  </section>
                )}

                {/* Incident record */}
                <section>
                  <div className={classes.sectionTitle}>Incident record</div>
                  <div className={classes.sectionNote}>Incidents {recordNoun}. Select a number to open the list.</div>
                  <div className={classes.counts}>
                    <Link className={classes.count} to={`/incidents?status=${OPEN_STATUSES.join(',')}`}>
                      <div className={classes.label}>Open</div>
                      <div className={classes.countValue}>{counts.open ?? '–'}</div>
                    </Link>
                    <Link className={classes.count} to={`/incidents?status=${DONE.join(',')}`}>
                      <div className={classes.label}>Resolved or closed</div>
                      <div className={classes.countValue}>{counts.done ?? '–'}</div>
                    </Link>
                    <Link className={classes.count} to="/incidents">
                      <div className={classes.label}>All time</div>
                      <div className={classes.countValue}>{counts.total ?? '–'}</div>
                    </Link>
                  </div>
                </section>

                {isTech && (
                  <>
                    {/* Escalation ladder */}
                    <section>
                      <div className={classes.sectionTitle}>Escalation ladder</div>
                      <div className={classes.sectionNote}>
                        When an SLA is at risk, the incident climbs one rung at a time until someone with the authority to
                        act has it.
                      </div>
                      <ol className={classes.ladder}>
                        {LADDER.map((l, index) => (
                          <li
                            key={l.level}
                            className={classes.rung}
                            data-current={index === levelIndex || undefined}
                            data-passed={index < levelIndex || undefined}
                            aria-current={index === levelIndex ? 'true' : undefined}
                          >
                            <div className={classes.rungLevel}>{l.short}</div>
                            <div className={classes.rungName}>{l.name}</div>
                            <div className={classes.rungText}>{l.text}</div>
                            {index === levelIndex && (
                              <div className={classes.label} style={{ marginTop: 8 }}>
                                You are here
                              </div>
                            )}
                          </li>
                        ))}
                      </ol>
                    </section>

                    {/* Workload */}
                    {max > 0 && (
                      <section>
                        <div className={classes.sectionTitle}>Workload</div>
                        <div className={classes.sectionNote}>
                          {active} of {max} slots in use.{' '}
                          {active >= max
                            ? 'You are at capacity: new automatic assignments go to someone else.'
                            : `${max - active} free for new assignments.`}
                        </div>
                        <div className={classes.slots} role="img" aria-label={`${active} of ${max} incident slots in use`}>
                          {Array.from({ length: Math.max(max, active) }, (_, i) => (
                            <span key={i} className={classes.slot} data-used={i < Math.min(active, max) || undefined} data-over={i >= max || undefined} />
                          ))}
                        </div>
                      </section>
                    )}

                    {/* Availability */}
                    <section>
                      <div className={classes.sectionTitle}>Availability</div>
                      <div className={classes.sectionNote}>
                        Only available technicians receive new automatic assignments. Incidents you already own stay with you.
                      </div>
                      <div className={classes.availability} role="group" aria-label="Availability">
                        {AVAILABILITY.map((a) => {
                          const selected = (p.availability ?? 'AVAILABLE') === a;
                          const Icon = AVAILABILITY_ICON[a];
                          return (
                            <Button
                              key={a}
                              variant={selected ? 'filled' : 'outline'}
                              size="md"
                              aria-pressed={selected}
                              leftSection={selected ? <IconCheck size={18} stroke={2.4} /> : <Icon size={18} stroke={1.8} />}
                              loading={setAvailability.isPending && setAvailability.variables === a}
                              disabled={setAvailability.isPending && setAvailability.variables !== a}
                              onClick={() => !selected && setAvailability.mutate(a)}
                              className={classes.availabilityButton}
                            >
                              {humanize(a)}
                            </Button>
                          );
                        })}
                      </div>
                      <div className={classes.availabilityStatus}>
                        {(p.availability ?? 'AVAILABLE') === 'AVAILABLE'
                          ? 'You are receiving new assignments.'
                          : `You are marked ${humanize(p.availability).toLowerCase()}: no new automatic assignments.`}
                      </div>
                    </section>

                    {/* Skills */}
                    {skills.length > 0 && (
                      <section>
                        <div className={classes.sectionTitle}>Skills</div>
                        <div className={classes.sectionNote}>
                          Proficiency out of 5. Skill-based assignment ranks you higher for these categories.
                        </div>
                        <div className={classes.skills}>
                          {skills.map((s) => (
                            <div key={s.categoryId} className={classes.skill}>
                              <Text size="sm" fw={500}>
                                {s.categoryName}
                              </Text>
                              <span className={classes.pips} role="img" aria-label={`${s.proficiency} out of 5`}>
                                {[1, 2, 3, 4, 5].map((n) => (
                                  <span key={n} className={classes.pip} data-on={n <= s.proficiency || undefined} />
                                ))}
                              </span>
                            </div>
                          ))}
                        </div>
                      </section>
                    )}
                  </>
                )}
              </>
            );
          })()}
      </QueryState>
    </Stack>
  );
}
