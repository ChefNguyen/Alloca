import React, { useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { WEEKDAYS } from '@/lib/weekdays';
import { getSchedules, replaceSchedules, ScheduleWindowInput } from '@/services/resourceApi';
import { DayOfWeek, Resource } from '@/types/api';

interface TimeRange {
  start: string;
  end: string;
}

type WeekState = Record<DayOfWeek, TimeRange[]>;

interface ResourceScheduleModalProps {
  resource: Resource;
  onClose: () => void;
  onSaved: (resource: Resource) => void;
}

const MAX_WINDOWS = 21;
const DEFAULT_RANGE: TimeRange = { start: '08:00', end: '18:00' };
const WORKDAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

const emptyWeek = (): WeekState =>
  Object.fromEntries(WEEKDAYS.map((d) => [d.key, [] as TimeRange[]])) as unknown as WeekState;

// "08:00:00" -> "08:00" for <input type="time">; keeps seconds when they are not zero.
const toInputTime = (time: string) => (/^\d\d:\d\d:00$/.test(time) ? time.slice(0, 5) : time);

const defaultWeek = (): WeekState => {
  const week = emptyWeek();
  WORKDAYS.forEach((day) => {
    week[day] = [{ ...DEFAULT_RANGE }];
  });
  return week;
};

const toInputs = (week: WeekState): ScheduleWindowInput[] =>
  WEEKDAYS.flatMap(({ key }) =>
    week[key].map((range) => ({ dayOfWeek: key, startTime: range.start, endTime: range.end })),
  );

/** Client-side mirror of the server rules, so obvious mistakes never leave the form. */
const validate = (week: WeekState): Partial<Record<DayOfWeek, string>> & { form?: string } => {
  const errors: Partial<Record<DayOfWeek, string>> & { form?: string } = {};
  let total = 0;
  WEEKDAYS.forEach(({ key }) => {
    const ranges = week[key];
    total += ranges.length;
    if (ranges.some((r) => !r.start || !r.end)) {
      errors[key] = 'Fill in both start and end times.';
      return;
    }
    if (ranges.some((r) => r.start >= r.end)) {
      errors[key] = 'Each window must end after it starts.';
      return;
    }
    const sorted = [...ranges].sort((a, b) => a.start.localeCompare(b.start));
    if (sorted.some((r, i) => i > 0 && r.start < sorted[i - 1].end)) {
      errors[key] = 'Windows on the same day must not overlap.';
    }
  });
  if (total === 0) errors.form = 'Add at least one operating window. To stop bookings, suspend the resource instead.';
  if (total > MAX_WINDOWS) errors.form = `At most ${MAX_WINDOWS} windows per week.`;
  return errors;
};

const timeInputClass =
  'h-9 w-28 px-2.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0';

export const ResourceScheduleModal: React.FC<ResourceScheduleModalProps> = ({ resource, onClose, onSaved }) => {
  const [week, setWeek] = useState<WeekState>(emptyWeek);
  const [loading, setLoading] = useState(true);
  const [usingSuggestion, setUsingSuggestion] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [showErrors, setShowErrors] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getSchedules(resource.id)
      .then((schedules) => {
        if (cancelled) return;
        if (schedules.length === 0) {
          setWeek(defaultWeek());
          setUsingSuggestion(true);
          return;
        }
        const loaded = emptyWeek();
        schedules.forEach((s) =>
          loaded[s.dayOfWeek].push({ start: toInputTime(s.startTime), end: toInputTime(s.endTime) }),
        );
        setWeek(loaded);
      })
      .catch((error) => {
        if (!cancelled) setLoadError(error instanceof ApiError ? error.message : 'Could not load operating hours.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [resource.id]);

  const edit = (update: (current: WeekState) => WeekState) => {
    setWeek(update);
    setServerError(null);
  };

  const setRange = (day: DayOfWeek, index: number, patch: Partial<TimeRange>) =>
    edit((current) => ({
      ...current,
      [day]: current[day].map((range, i) => (i === index ? { ...range, ...patch } : range)),
    }));

  const addRange = (day: DayOfWeek) =>
    edit((current) => ({ ...current, [day]: [...current[day], { ...DEFAULT_RANGE }] }));

  const removeRange = (day: DayOfWeek, index: number) =>
    edit((current) => ({ ...current, [day]: current[day].filter((_, i) => i !== index) }));

  const copyMondayToWorkdays = () =>
    edit((current) => {
      const next = { ...current };
      WORKDAYS.slice(1).forEach((day) => {
        next[day] = current.MONDAY.map((range) => ({ ...range }));
      });
      return next;
    });

  const errors = validate(week);
  const hasErrors = Object.keys(errors).length > 0;

  const handleSave = async () => {
    setShowErrors(true);
    if (hasErrors) return;
    setSaving(true);
    setServerError(null);
    try {
      await replaceSchedules(resource.id, toInputs(week));
      onSaved(resource);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        const ids = error.details.conflictingBookingIds;
        const list = Array.isArray(ids) ? ` (${ids.join(', ')})` : '';
        setServerError(
          `These hours would leave ${Array.isArray(ids) ? ids.length : 'some'} existing CONFIRMED booking(s) outside them${list}. ` +
            'Nothing was changed — cancel or move those bookings first.',
        );
      } else {
        setServerError(error instanceof ApiError ? error.message : 'Could not save operating hours.');
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
      <div className="w-full max-w-2xl bg-[#FAFAF8] rounded-[8px] shadow-hero overflow-hidden">
        <div className="px-6 py-4 border-b border-[#E5E0D8]/60 flex items-center justify-between bg-[#F0EDE8]/60">
          <div>
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              Operating Hours
            </span>
            <h2 className="text-base font-bold text-[#242424] font-editorial">{resource.name}</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="w-8 h-8 rounded-[4px] flex items-center justify-center text-[#8A8070] hover:text-[#242424] hover:bg-[#EAE5DC] transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
          {loading && <p className="text-xs text-[#8A8070]">Loading operating hours…</p>}
          {loadError && (
            <div role="alert" className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold">
              {loadError}
            </div>
          )}

          {!loading && !loadError && (
            <>
              <p className="text-xs text-[#8A8070]">
                Times are in Vietnam time (UTC+7). A day without hours is closed. Windows that touch (08:00–12:00 and
                12:00–17:00) are treated as one continuous window.
              </p>
              {usingSuggestion && (
                <div className="p-3 rounded-[6px] bg-[#FFF3E0] text-[#E65100] text-xs font-semibold">
                  No hours saved yet. Monday–Friday 08:00–18:00 is suggested below — nothing is saved until you click
                  Save.
                </div>
              )}
              {serverError && (
                <div role="alert" className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold">
                  {serverError}
                </div>
              )}
              {showErrors && errors.form && (
                <div role="alert" className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold">
                  {errors.form}
                </div>
              )}

              <div className="divide-y divide-[#E5E0D8]/50 border border-[#E5E0D8]/60 rounded-[6px]">
                {WEEKDAYS.map(({ key, label }) => (
                  <div key={key} className="p-3 flex items-start gap-4">
                    <span className="w-24 pt-2 text-xs font-semibold text-[#242424] shrink-0">{label}</span>
                    <div className="flex-1 space-y-2">
                      {week[key].length === 0 && (
                        <span className="inline-block mt-1.5 text-xs font-medium text-[#8A8070] bg-[#F0EDE8] px-2.5 py-1 rounded-[4px]">
                          Closed
                        </span>
                      )}
                      {week[key].map((range, index) => (
                        <div key={index} className="flex items-center gap-2">
                          <input
                            type="time"
                            aria-label={`${label} window ${index + 1} start`}
                            value={range.start}
                            onChange={(e) => setRange(key, index, { start: e.target.value })}
                            className={timeInputClass}
                          />
                          <span className="text-xs text-[#8A8070]">to</span>
                          <input
                            type="time"
                            aria-label={`${label} window ${index + 1} end`}
                            value={range.end}
                            onChange={(e) => setRange(key, index, { end: e.target.value })}
                            className={timeInputClass}
                          />
                          <button
                            type="button"
                            onClick={() => removeRange(key, index)}
                            aria-label={`Remove ${label} window ${index + 1}`}
                            className="w-8 h-8 rounded-[4px] flex items-center justify-center text-[#8A8070] hover:text-[#C62828] hover:bg-[#F0EDE8] cursor-pointer"
                          >
                            <span className="material-symbols-outlined text-[16px]">delete</span>
                          </button>
                        </div>
                      ))}
                      {showErrors && errors[key] && (
                        <p className="text-[11px] text-[#C62828] font-medium">{errors[key]}</p>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => addRange(key)}
                      className="pt-2 text-xs font-semibold text-[#242424] hover:underline cursor-pointer shrink-0"
                    >
                      + Add hours
                    </button>
                  </div>
                ))}
              </div>

              <button
                type="button"
                onClick={copyMondayToWorkdays}
                className="text-xs font-semibold text-[#242424] hover:underline cursor-pointer"
              >
                Copy Monday&apos;s hours to Tuesday–Friday
              </button>
            </>
          )}
        </div>

        <div className="px-6 py-4 border-t border-[#E5E0D8]/60 flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2.5 rounded-[6px] text-[#8A8070] hover:text-[#242424] hover:bg-[#F0EDE8] font-semibold text-xs transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving || loading || loadError !== null}
            className="px-5 py-2.5 rounded-[6px] bg-[#D97757] text-white font-semibold text-xs hover:opacity-95 shadow-card transition-all cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {saving ? 'Saving…' : 'Save Hours'}
          </button>
        </div>
      </div>
    </div>
  );
};
