import { useMutation, useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { StyleSheet, Text, View } from 'react-native';
import { api } from '../api/client';
import { errorMessage } from '../lib/format';
import { Button, T } from './kit';
import { FIGURE, useColors } from './theme';

const RISK_LABEL = { LOW: 'LOW RISK', MEDIUM: 'MEDIUM RISK', HIGH: 'BREACH LIKELY' } as const;

function time(iso: string) {
  const d = dayjs(iso);
  return d.isSame(dayjs(), 'day') ? d.format('HH:mm') : d.format('D MMM HH:mm');
}

/** Predictive SLA for support staff: breach chance, expected finish against the deadline, and why. */
export function SlaForecastCard({ incidentId }: { incidentId: number }) {
  const c = useColors();
  const forecast = useQuery({
    queryKey: ['incidents', incidentId, 'sla-forecast'],
    queryFn: () => api.incidents.slaForecast(incidentId),
    refetchInterval: 60_000,
  });
  const f = forecast.data;
  if (!f) return null;
  const high = f.risk === 'HIGH';
  const ink = high ? c.danger : c.primary;
  const now = dayjs();
  const end = Math.max(dayjs(f.expectedFinish).diff(now), dayjs(f.deadline).diff(now), 1);
  const finishPct = Math.min(100, (dayjs(f.expectedFinish).diff(now) / end) * 100);
  const deadlinePct = Math.min(100, (dayjs(f.deadline).diff(now) / end) * 100);
  const late = dayjs(f.expectedFinish).isAfter(dayjs(f.deadline));

  return (
    <View style={[styles.card, { backgroundColor: c.surface, borderColor: high ? c.danger : c.border, borderWidth: high ? 2 : StyleSheet.hairlineWidth }]}>
      <Text style={[styles.label, { color: c.primary }]}>SLA FORECAST</Text>
      <View style={styles.figureRow}>
        <Text style={[styles.figure, { color: ink }]}>{Math.round(f.probability * 100)}%</Text>
        <View style={[styles.risk, { borderColor: ink, borderWidth: high ? 2.5 : 1.25 }]}>
          <Text style={[styles.riskText, { color: ink }]}>{RISK_LABEL[f.risk]}</Text>
        </View>
      </View>
      <T v="small" muted>
        chance of missing the resolution deadline
      </T>
      <View style={[styles.track, { borderColor: c.border }]} accessible accessibilityLabel={`Expected finish ${time(f.expectedFinish)}, deadline ${time(f.deadline)}`}>
        <View style={{ width: `${finishPct}%`, height: '100%', backgroundColor: late ? c.danger : c.primary }} />
        <View style={[styles.deadline, { left: `${deadlinePct}%`, backgroundColor: c.danger }]} />
      </View>
      <View style={styles.legend}>
        <View>
          <Text style={[styles.label, { color: c.primary }]}>EXPECTED FINISH</Text>
          <Text style={[styles.time, { color: c.text }]}>{time(f.expectedFinish)}</Text>
        </View>
        <View style={{ alignItems: 'flex-end' }}>
          <Text style={[styles.label, { color: c.primary }]}>DEADLINE</Text>
          <Text style={[styles.time, { color: c.text }]}>{time(f.deadline)}</Text>
        </View>
      </View>
      <View style={[styles.reasons, { borderTopColor: c.divider }]}>
        {f.reasons.map((r) => (
          <T key={r} v="small">
            • {r}
          </T>
        ))}
      </View>
      <T v="small" muted>
        {f.lowConfidence ? `Low confidence: only ${f.sampleSize} comparable resolved incidents.` : `Learned from ${f.sampleSize} resolved ${f.basis.toLowerCase()}.`}
      </T>
    </View>
  );
}

/** Support staff: troubleshooting steps from similar resolved incidents (Gemini), labelled as AI output. */
export function AiFixCard({ incidentId }: { incidentId: number }) {
  const c = useColors();
  const status = useQuery({ queryKey: ['ai', 'status'], queryFn: api.ai.status, staleTime: 5 * 60_000 });
  const suggest = useMutation({ mutationFn: () => api.incidents.aiSuggestion(incidentId) });
  if (!status.data?.available) return null;
  const s = suggest.data;

  return (
    <View style={[styles.card, { backgroundColor: c.surface, borderColor: c.border, borderWidth: StyleSheet.hairlineWidth }]}>
      <Text style={[styles.label, { color: c.primary }]}>AI FIX SUGGESTION</Text>
      {!s ? (
        <>
          <T v="small" muted>
            Troubleshooting steps drafted from similar incidents that were already resolved.
          </T>
          <Button label="Suggest a fix" icon="sparkles-outline" variant="outlined" loading={suggest.isPending} onPress={() => suggest.mutate()} />
          {suggest.error ? <T v="small" color={c.danger}>{errorMessage(suggest.error)}</T> : null}
        </>
      ) : (
        <>
          <T v="small">{s.summary}</T>
          {s.steps.map((step, i) => (
            <View key={step} style={{ flexDirection: 'row', gap: 8 }}>
              <Text style={{ fontFamily: FIGURE, color: c.primary, fontSize: 14 }}>{i + 1}.</Text>
              <T v="small" style={{ flex: 1 }}>
                {step}
              </T>
            </View>
          ))}
          {s.likelyCause ? (
            <View style={[styles.reasons, { borderTopColor: c.divider }]}>
              <Text style={[styles.label, { color: c.primary }]}>LIKELY CAUSE</Text>
              <T v="small">{s.likelyCause}</T>
            </View>
          ) : null}
          {s.basedOn.length > 0 && (
            <T v="small" muted>
              Based on {s.basedOn.join(', ')}
            </T>
          )}
          <T v="small" muted>
            Generated by Google Gemini ({s.model}). Check it before acting on it.
          </T>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: 14, padding: 16, gap: 8 },
  label: { fontSize: 10.5, fontWeight: '800', letterSpacing: 1.1 },
  figureRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  figure: { fontFamily: FIGURE, fontSize: 38 },
  risk: { paddingHorizontal: 6, paddingVertical: 2 },
  riskText: { fontSize: 10.5, fontWeight: '800', letterSpacing: 1 },
  track: { height: 10, borderWidth: 1, marginTop: 6 },
  deadline: { position: 'absolute', top: -5, bottom: -5, width: 2, marginLeft: -1 },
  legend: { flexDirection: 'row', justifyContent: 'space-between' },
  time: { fontFamily: FIGURE, fontSize: 15 },
  reasons: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: 8, gap: 4 },
});
