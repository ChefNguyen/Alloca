import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { businessDate, dateAfter } from '@/lib/bookingTime';
import { analyticsApi } from '@/services/analyticsApi';
import { listDepartments, listResources } from '@/services/resourceApi';
import {
  BookingTrendPoint,
  Department,
  Resource,
  UtilizationAlert,
  UtilizationMetric,
} from '@/types/api';

export const Analytics: React.FC = () => {
  const { user, role } = useAuth();
  const isAdmin = role === 'ADMIN';
  const isManager = role === 'MANAGER';

  const [reportPeriod, setReportPeriod] = useState<'7d' | '30d'>('7d');
  const [departments, setDepartments] = useState<Department[]>([]);
  const [resources, setResources] = useState<Resource[]>([]);
  const [selectedDeptId, setSelectedDeptId] = useState<string>(
    isManager && user?.department ? user.department.id : ''
  );
  const [selectedResourceId, setSelectedResourceId] = useState<string>('');

  const [metrics, setMetrics] = useState<UtilizationMetric[]>([]);
  const [trendPoints, setTrendPoints] = useState<BookingTrendPoint[]>([]);
  const [alerts, setAlerts] = useState<UtilizationAlert[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Load department list for admin
  useEffect(() => {
    if (isAdmin) {
      listDepartments()
        .then((depts) => setDepartments(depts))
        .catch(() => {});
    }
  }, [isAdmin]);

  // Load resources for dropdown (filtered by department if applicable)
  useEffect(() => {
    const deptId = isManager && user?.department ? user.department.id : selectedDeptId;
    listResources(deptId ? { departmentId: deptId, size: 100 } : { size: 100 })
      .then((resPage) => {
        let items = resPage.items;
        if (isManager) {
          items = items.filter((r) => r.department?.id === user?.department?.id);
        }
        setResources(items);
      })
      .catch(() => {});
  }, [isAdmin, isManager, user?.department, selectedDeptId]);

  // Effective department ID for querying analytics API
  const effectiveDeptId = useMemo(() => {
    if (isManager) {
      return user?.department?.id || undefined;
    }
    return selectedDeptId || undefined;
  }, [isManager, user?.department, selectedDeptId]);

  // Fetch live telemetry, trends, and alerts
  const loadData = useCallback(async () => {
    if (isManager && !user?.department) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    setActionError(null);

    const todayStr = businessDate();
    const fromStr = dateAfter(todayStr, reportPeriod === '7d' ? -7 : -30);

    try {
      const [utilRes, trendRes, alertRes] = await Promise.all([
        analyticsApi.getUtilization({
          from: fromStr,
          to: todayStr,
          departmentId: effectiveDeptId,
          resourceId: selectedResourceId || undefined,
        }),
        analyticsApi.getBookingTrends({
          from: fromStr,
          to: todayStr,
          departmentId: effectiveDeptId,
          resourceId: selectedResourceId || undefined,
        }),
        analyticsApi.getAlerts({
          departmentId: effectiveDeptId,
          resourceId: selectedResourceId || undefined,
          size: 50,
        }),
      ]);

      setMetrics(utilRes.metrics || []);
      setTrendPoints(trendRes.points || []);
      setAlerts(alertRes.items || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load analytics telemetry';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [effectiveDeptId, isManager, user?.department, reportPeriod, selectedResourceId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Acknowledge alert
  const handleAcknowledge = async (alertId: string) => {
    setActionError(null);
    try {
      const updated = await analyticsApi.acknowledgeAlert(alertId);
      setAlerts((prev) =>
        prev.map((a) => (a.id === alertId ? { ...a, status: updated.status, acknowledgedAt: updated.acknowledgedAt } : a))
      );
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to acknowledge alert';
      setActionError(msg);
    }
  };

  // Compute KPI summaries
  const totalOperatingMinutes = useMemo(
    () => metrics.reduce((acc, m) => acc + m.operatingMinutes, 0),
    [metrics]
  );
  const totalScheduledMinutes = useMemo(
    () => metrics.reduce((acc, m) => acc + m.scheduledMinutes, 0),
    [metrics]
  );
  const overallBookedRatio = useMemo(() => {
    if (totalOperatingMinutes === 0) return null;
    return Math.round((totalScheduledMinutes * 100.0) / totalOperatingMinutes * 10) / 10;
  }, [totalOperatingMinutes, totalScheduledMinutes]);

  const totalBookedHours = useMemo(
    () => (totalScheduledMinutes / 60).toFixed(1),
    [totalScheduledMinutes]
  );

  const activeAlertsCount = useMemo(
    () => alerts.filter((a) => a.status === 'OPEN').length,
    [alerts]
  );

  const peakDemandSpace = useMemo(() => {
    if (metrics.length === 0) return null;
    const sorted = [...metrics].sort((a, b) => (b.utilizationPercent || 0) - (a.utilizationPercent || 0));
    return sorted[0];
  }, [metrics]);

  return (
    <div className="w-full min-h-screen bg-[#F0EDE8] p-6 lg:p-8 space-y-6">
      {/* Top Editorial Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 pb-4 border-b border-[#E5E0D8]/60">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070]">
              Telemetry & Capacity Utilization
            </span>
            {isAdmin && (
              <span className="px-2 py-0.5 rounded-[4px] bg-[#242424] text-white text-[9px] font-bold tracking-wider uppercase">
                Admin · Cross-System
              </span>
            )}
            {isManager && user?.department && (
              <span className="px-2 py-0.5 rounded-[4px] bg-[#D97757]/15 text-[#D97757] border border-[#D97757]/30 text-[9px] font-bold tracking-wider uppercase">
                {user.department.name} · Scoped
              </span>
            )}
          </div>
          <h1 className="text-[48px] font-[300] text-[#242424] tracking-[0.06em] leading-none font-editorial">
            Space analytics
          </h1>
        </div>

        {/* Period Selector Buttons */}
        <div className="inline-flex p-1 bg-[#FAFAF8] shadow-card rounded-[6px] gap-1 self-start md:self-auto">
          <button
            onClick={() => setReportPeriod('7d')}
            className={`px-3.5 py-1.5 rounded-[4px] text-xs font-semibold transition-all cursor-pointer ${
              reportPeriod === '7d'
                ? 'bg-[#242424] text-white shadow-xs'
                : 'text-[#8A8070] hover:text-[#242424]'
            }`}
          >
            Rolling 7 Days
          </button>
          <button
            onClick={() => setReportPeriod('30d')}
            className={`px-3.5 py-1.5 rounded-[4px] text-xs font-semibold transition-all cursor-pointer ${
              reportPeriod === '30d'
                ? 'bg-[#242424] text-white shadow-xs'
                : 'text-[#8A8070] hover:text-[#242424]'
            }`}
          >
            Monthly Period
          </button>
        </div>
      </div>

      {/* Scope and Filter Controls (ALC-51, ALC-52) */}
      <div className="p-4 rounded-[8px] bg-[#FAFAF8] shadow-card flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-4">
          {/* Department Filter */}
          <div className="flex items-center gap-2">
            <label className="text-[11px] font-semibold text-[#8A8070] uppercase tracking-wider">
              Department:
            </label>
            {isAdmin ? (
              <select
                value={selectedDeptId}
                onChange={(e) => {
                  setSelectedDeptId(e.target.value);
                  setSelectedResourceId('');
                }}
                className="px-3 py-1.5 bg-white border border-[#E5E0D8] rounded-[6px] text-xs font-medium text-[#242424] focus:outline-none focus:border-[#D97757]"
              >
                <option value="">All Departments (Company-wide)</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            ) : isManager && user?.department ? (
              <div className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#F0EDE8]/60 border border-[#E5E0D8] rounded-[6px] text-xs font-semibold text-[#242424]">
                <span>🔒 {user.department.name}</span>
                <span className="text-[10px] text-[#8A8070] font-normal">(Department scoped)</span>
              </div>
            ) : (
              <span className="text-xs text-[#C62828] font-medium">No department assigned</span>
            )}
          </div>

          {/* Resource Filter */}
          <div className="flex items-center gap-2">
            <label className="text-[11px] font-semibold text-[#8A8070] uppercase tracking-wider">
              Space:
            </label>
            <select
              value={selectedResourceId}
              onChange={(e) => setSelectedResourceId(e.target.value)}
              className="px-3 py-1.5 bg-white border border-[#E5E0D8] rounded-[6px] text-xs font-medium text-[#242424] focus:outline-none focus:border-[#D97757]"
            >
              <option value="">All Spaces</option>
              {resources.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <button
          onClick={loadData}
          disabled={loading}
          className="px-3.5 py-1.5 rounded-[6px] bg-[#242424] text-white text-xs font-semibold hover:bg-black transition-colors disabled:opacity-50 cursor-pointer"
        >
          {loading ? 'Refreshing...' : 'Refresh Telemetry'}
        </button>
      </div>

      {/* Error Banners */}
      {error && (
        <div
          role="alert"
          className="p-4 rounded-[6px] bg-[#FFEBEE] border border-[#E05252]/30 text-[#C62828] text-xs font-semibold flex items-center justify-between"
        >
          <span>{error}</span>
          <button onClick={loadData} className="underline text-[11px] cursor-pointer">
            Retry
          </button>
        </div>
      )}
      {actionError && (
        <div
          role="alert"
          className="p-3 rounded-[6px] bg-[#FFEBEE] border border-[#E05252]/30 text-[#C62828] text-xs font-semibold"
        >
          {actionError}
        </div>
      )}

      {/* Missing Department warning for Managers */}
      {isManager && !user?.department && (
        <div
          role="alert"
          className="p-6 rounded-[8px] bg-[#FFF3E0] border border-[#E8A020]/40 text-[#E65100] text-sm font-medium"
        >
          Your account is not assigned to a department. In accordance with department-scoping rules (ALC-52),
          managers can only view telemetry and alerts for spaces owned by their department. Please contact an administrator.
        </div>
      )}

      {/* KPI Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-2 border-0">
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
            Overall Booked Ratio
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-[52px] font-bold text-[#242424] leading-none tracking-tight">
              {overallBookedRatio !== null ? `${overallBookedRatio}%` : 'N/A'}
            </span>
          </div>
          <div className="w-full h-1.5 bg-[#F0EDE8] rounded-[4px] overflow-hidden mt-2">
            <div
              className="h-full bg-[#D4CFC9] rounded-[4px]"
              style={{ width: `${Math.min(overallBookedRatio || 0, 100)}%` }}
            />
          </div>
          <p className="text-[11px] text-[#8A8070] pt-1">Across configured operating windows</p>
        </div>

        <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-2 border-0">
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
            Total Booked Hours
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-[52px] font-bold text-[#242424] leading-none tracking-tight">
              {totalBookedHours}h
            </span>
            <span className="text-xs text-[#8A8070] font-medium">{metrics.length} spaces</span>
          </div>
          <div className="w-full h-1.5 bg-[#F0EDE8] rounded-[4px] overflow-hidden mt-2">
            <div
              className="h-full bg-[#D4CFC9] rounded-[4px]"
              style={{ width: `${Math.min(Number(totalBookedHours) * 2, 100)}%` }}
            />
          </div>
          <p className="text-[11px] text-[#8A8070] pt-1">Confirmed & completed intervals</p>
        </div>

        {/* ALERTS CARD: uses #E8622A (allowed for alerts only) */}
        <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-2 border-0">
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
            Active Exceeded Alerts
          </span>
          <div className="flex items-baseline gap-2">
            <span className={`text-[52px] font-bold leading-none tracking-tight ${activeAlertsCount > 0 ? 'text-[#E8622A]' : 'text-[#242424]'}`}>
              {activeAlertsCount}
            </span>
            <span className="text-xs text-[#8A8070] font-medium">units &gt; threshold</span>
          </div>
          <div className="w-full h-1.5 bg-[#F0EDE8] rounded-[4px] overflow-hidden mt-2">
            <div
              className={`h-full rounded-[4px] ${activeAlertsCount > 0 ? 'bg-[#E8622A]' : 'bg-[#4CAF7D]'}`}
              style={{ width: `${Math.min(activeAlertsCount * 25, 100)}%` }}
            />
          </div>
          <p className="text-[11px] text-[#8A8070] pt-1">Daily evaluation at 00:05 local time</p>
        </div>

        <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-2 border-0">
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
            Peak Demand Space
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-[32px] font-bold text-[#242424] leading-tight tracking-tight truncate max-w-[200px]" title={peakDemandSpace?.resource.name}>
              {peakDemandSpace ? peakDemandSpace.resource.name : '—'}
            </span>
          </div>
          <p className="text-[11px] text-[#8A8070] pt-1">
            {peakDemandSpace && peakDemandSpace.utilizationPercent !== null
              ? `Utilization rate: ${peakDemandSpace.utilizationPercent}% (${peakDemandSpace.scheduledMinutes} min)`
              : 'No booking demand recorded in period'}
          </p>
        </div>
      </div>

      {/* Main Analysis Grid */}
      <div className="grid grid-cols-12 gap-6 items-start">
        {/* Left Column (8 cols): Rolling Chart & Metrics Breakdown */}
        <div className="col-span-12 lg:col-span-8 space-y-6">
          {/* Visual Rolling Chart */}
          <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-4 border-0">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                  Daily Utilization & Booking Trends
                </span>
                <h2 className="text-lg font-light text-[#242424] font-editorial mt-0.5">
                  Start Date Distribution ({reportPeriod === '7d' ? '7 Days' : '30 Days'})
                </h2>
              </div>
              <div className="flex items-center gap-4 text-xs">
                <span className="flex items-center gap-1.5 text-[#242424] font-medium">
                  <span className="w-2.5 h-2.5 rounded-[2px] bg-[#D97757]" /> Confirmed / Completed
                </span>
                <span className="flex items-center gap-1.5 text-[#8A8070] font-medium">
                  <span className="w-2.5 h-2.5 rounded-[2px] bg-[#D4CFC9]" /> Cancelled
                </span>
              </div>
            </div>

            {/* Daily Bars */}
            <div className="h-64 w-full flex items-end justify-between gap-2 pt-6 pb-2 px-4 bg-[#F0EDE8]/40 rounded-[6px] relative overflow-x-auto">
              {trendPoints.length === 0 ? (
                <div className="w-full h-full flex items-center justify-center text-xs text-[#8A8070]">
                  No trend data available for the selected period
                </div>
              ) : (
                trendPoints.map((item) => {
                  const activeTotal = item.confirmedCount + item.completedCount;
                  const grandTotal = activeTotal + item.cancelledCount;
                  const heightPct = grandTotal > 0 ? Math.min((grandTotal / 10) * 100, 100) : 4;
                  return (
                    <div key={item.date} className="flex-1 min-w-[28px] flex flex-col items-center h-full justify-end z-10 group">
                      <span className="text-[10px] font-bold text-[#242424] mb-1">
                        {activeTotal}
                      </span>
                      <div
                        style={{ height: `${Math.max(heightPct, 8)}%` }}
                        className="w-full max-w-[36px] rounded-t-[4px] bg-[#D97757] shadow-xs flex flex-col justify-end overflow-hidden"
                      >
                        {item.cancelledCount > 0 && (
                          <div
                            style={{ height: `${(item.cancelledCount / grandTotal) * 100}%` }}
                            className="w-full bg-[#D4CFC9]"
                            title={`Cancelled: ${item.cancelledCount}`}
                          />
                        )}
                      </div>
                      <span className="text-[9px] text-[#8A8070] mt-2 font-medium tracking-wide truncate max-w-full">
                        {item.date.slice(5)}
                      </span>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* Resource Telemetry Breakdown Table */}
          <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-4 border-0">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                  Detailed Telemetry Breakdown
                </span>
                <h2 className="text-lg font-light text-[#242424] font-editorial mt-0.5">
                  Operating Window Ratios
                </h2>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-[#E5E0D8]/60 bg-[#F0EDE8]/50 text-[#8A8070] uppercase font-bold text-[10px] tracking-wider">
                    <th className="py-3 px-3.5 rounded-l-[4px]">Resource</th>
                    <th className="py-3 px-3.5">Department</th>
                    <th className="py-3 px-3.5">Operating Windows</th>
                    <th className="py-3 px-3.5">Booked Minutes</th>
                    <th className="py-3 px-3.5">Utilization Rate</th>
                    <th className="py-3 px-3.5 rounded-r-[4px]">Threshold</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E5E0D8]/40">
                  {metrics.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="py-8 text-center text-xs text-[#8A8070]">
                        No resources matching the current scope
                      </td>
                    </tr>
                  ) : (
                    metrics.map((m) => {
                      const threshold = m.resource.utilizationThreshold || 85.0;
                      const isExceeded = m.utilizationPercent !== null && m.utilizationPercent > threshold;
                      return (
                        <tr key={m.resource.id} className="hover:bg-[#F0EDE8]/30 transition-colors">
                          <td className="py-3.5 px-3.5 font-semibold text-[#242424]">{m.resource.name}</td>
                          <td className="py-3.5 px-3.5 text-[#8A8070]">
                            {m.resource.department ? m.resource.department.name : 'Shared'}
                          </td>
                          <td className="py-3.5 px-3.5 text-[#8A8070]">{m.operatingMinutes} min</td>
                          <td className="py-3.5 px-3.5 text-[#8A8070]">{m.scheduledMinutes} min</td>
                          <td className="py-3.5 px-3.5">
                            {m.utilizationPercent === null ? (
                              <span className="text-[#8A8070] italic">N/A (0 min op)</span>
                            ) : (
                              <div className="flex items-center gap-2.5">
                                <span
                                  className={`font-bold ${
                                    isExceeded ? 'text-[#E8622A]' : 'text-[#242424]'
                                  }`}
                                >
                                  {m.utilizationPercent}%
                                </span>
                                <div className="w-24 h-2 bg-[#F0EDE8] rounded-[4px] overflow-hidden">
                                  <div
                                    style={{ width: `${Math.min(m.utilizationPercent, 100)}%` }}
                                    className={`h-full rounded-[4px] ${
                                      isExceeded ? 'bg-[#E8622A]' : 'bg-[#D4CFC9]'
                                    }`}
                                  />
                                </div>
                              </div>
                            )}
                          </td>
                          <td className="py-3.5 px-3.5 text-[#8A8070] font-medium">
                            {threshold}%
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Right Column (4 cols): Active Alerts Management */}
        <div className="col-span-12 lg:col-span-4 space-y-6">
          <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-4 border-0">
            <div className="flex items-center justify-between pb-3 border-b border-[#E5E0D8]/60">
              <div>
                <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                  Capacity Alerts
                </span>
                <h2 className="text-base font-bold text-[#242424]">Threshold Exceeded</h2>
              </div>
              <span className="text-[11px] text-[#8A8070]">Anti-spam enabled</span>
            </div>

            <p className="text-xs text-[#8A8070] leading-relaxed">
              Alerts evaluate rolling 7 completed days and trigger strictly when booked ratio &gt; threshold.
            </p>

            <div className="space-y-3">
              {alerts.length === 0 ? (
                <div className="p-6 text-center text-xs text-[#8A8070] bg-[#F0EDE8]/40 rounded-[6px]">
                  No capacity alerts recorded for this scope.
                </div>
              ) : (
                alerts.map((a) => (
                  <div
                    key={a.id}
                    className={`p-4 rounded-[6px] border flex flex-col gap-2 transition-all ${
                      a.status === 'OPEN'
                        ? 'border-[#E8622A]/40 bg-[#E8622A]/5'
                        : a.status === 'ACKNOWLEDGED'
                        ? 'border-[#E8A020]/40 bg-[#FFF3E0]'
                        : 'border-[#E5E0D8] bg-[#F0EDE8]/40'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-[#242424]">{a.resource.name}</span>
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-[4px] ${
                          a.status === 'OPEN'
                            ? 'bg-[#E8622A] text-white'
                            : a.status === 'ACKNOWLEDGED'
                            ? 'bg-[#E8A020] text-white'
                            : 'bg-[#4CAF7D] text-white'
                        }`}
                      >
                        {a.status}
                      </span>
                    </div>

                    <div className="text-xs text-[#242424]">
                      Rate:{' '}
                      <span className="font-bold text-[#E8622A]">{a.utilizationPercent}%</span> (Threshold:{' '}
                      {a.threshold}%)
                    </div>

                    <div className="text-[11px] text-[#8A8070]">
                      Window: {a.windowStart} – {a.windowEnd}
                    </div>

                    {a.status === 'RESOLVED' && a.resolutionReason && (
                      <div className="text-[10px] text-[#2E7D32] bg-[#E8F5EE] px-2 py-1 rounded-[4px]">
                        Resolved: {a.resolutionReason}
                      </div>
                    )}

                    {/* Actions */}
                    {a.status === 'OPEN' && (
                      <div className="flex items-center gap-2 pt-2.5 border-t border-[#E5E0D8]/60 mt-1">
                        <button
                          onClick={() => handleAcknowledge(a.id)}
                          className="px-3 py-1 rounded-[6px] bg-[#FAFAF8] text-[#242424] shadow-card hover:bg-white text-[11px] font-semibold cursor-pointer border border-[#E5E0D8]"
                        >
                          Acknowledge Alert
                        </button>
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
