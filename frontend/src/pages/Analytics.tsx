import React, { useState } from 'react';
import { mockUtilizationMetrics, mockAlerts } from '@/mock/data';
import { UtilizationAlert } from '@/types/api';

export const Analytics: React.FC = () => {
  const [alerts, setAlerts] = useState<UtilizationAlert[]>(mockAlerts);
  const [reportPeriod, setReportPeriod] = useState<'7d' | '30d'>('7d');

  const acknowledgeAlert = (id: string) => {
    setAlerts((prev) =>
      prev.map((a) =>
        a.id === id ? { ...a, status: 'ACKNOWLEDGED', acknowledgedAt: new Date().toISOString() } : a
      )
    );
  };

  const resolveAlert = (id: string) => {
    setAlerts((prev) =>
      prev.map((a) =>
        a.id === id ? { ...a, status: 'RESOLVED', resolvedAt: new Date().toISOString() } : a
      )
    );
  };

  return (
    <div className="w-full min-h-screen bg-[#F0EDE8] p-6 lg:p-8 space-y-6">
      {/* Top Editorial Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 pb-4 border-b border-[#E5E0D8]/60">
        <div>
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block mb-1">
            Telemetry & Capacity Utilization
          </span>
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

      {/* KPI Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-2 border-0">
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
            Overall Booked Ratio
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-[52px] font-bold text-[#242424] leading-none tracking-tight">74.2%</span>
            <span className="text-xs font-medium text-[#8A8070]">+8.4%</span>
          </div>
          {/* Warm Gray Progress Bar: #D4CFC9 */}
          <div className="w-full h-1.5 bg-[#F0EDE8] rounded-[4px] overflow-hidden mt-2">
            <div
              className="h-full bg-[#D4CFC9] rounded-[4px]"
              style={{ width: '74.2%' }}
            />
          </div>
          <p className="text-[11px] text-[#8A8070] pt-1">Across configured operating windows</p>
        </div>

        <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-2 border-0">
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
            Total Booked Hours
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-[52px] font-bold text-[#242424] leading-none tracking-tight">223.5h</span>
            <span className="text-xs text-[#8A8070] font-medium">8 spaces</span>
          </div>
          {/* Warm Gray Progress Bar */}
          <div className="w-full h-1.5 bg-[#F0EDE8] rounded-[4px] overflow-hidden mt-2">
            <div
              className="h-full bg-[#D4CFC9] rounded-[4px]"
              style={{ width: '65%' }}
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
            <span className="text-[52px] font-bold text-[#E8622A] leading-none tracking-tight">
              {alerts.filter((a) => a.status === 'OPEN').length}
            </span>
            <span className="text-xs text-[#8A8070] font-medium">units &gt; 85%</span>
          </div>
          {/* Alert Indicator */}
          <div className="w-full h-1.5 bg-[#F0EDE8] rounded-[4px] overflow-hidden mt-2">
            <div
              className="h-full bg-[#E8622A] rounded-[4px]"
              style={{ width: '40%' }}
            />
          </div>
          <p className="text-[11px] text-[#8A8070] pt-1">Daily evaluation at 00:05 local time</p>
        </div>

        <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-2 border-0">
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
            Peak Demand Hour
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-[52px] font-bold text-[#242424] leading-none tracking-tight">14:00</span>
            <span className="text-xs text-[#8A8070] font-medium">PM</span>
          </div>
          {/* Warm Gray Progress Bar */}
          <div className="w-full h-1.5 bg-[#F0EDE8] rounded-[4px] overflow-hidden mt-2">
            <div
              className="h-full bg-[#D4CFC9] rounded-[4px]"
              style={{ width: '88%' }}
            />
          </div>
          <p className="text-[11px] text-[#8A8070] pt-1">Highest concurrent room demand</p>
        </div>
      </div>

      {/* Main Analysis Grid */}
      <div className="grid grid-cols-12 gap-6 items-start">
        {/* Left Column (8 cols): 7-Day Rolling Chart & Metrics Breakdown */}
        <div className="col-span-12 lg:col-span-8 space-y-6">
          {/* Visual Rolling Chart */}
          <div className="p-6 rounded-[8px] bg-[#FAFAF8] shadow-card space-y-4 border-0">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                  Daily Utilization Distribution
                </span>
                <h2 className="text-lg font-light text-[#242424] font-editorial mt-0.5">
                  Rolling 7-day Completed Periods
                </h2>
              </div>
              <div className="flex items-center gap-4 text-xs">
                <span className="flex items-center gap-1.5 text-[#242424] font-medium">
                  <span className="w-2.5 h-2.5 rounded-[2px] bg-[#A89880]" /> Actual Booked %
                </span>
                <span className="flex items-center gap-1.5 text-[#8A8070] font-medium">
                  <span className="w-3 h-0.5 bg-[#E8622A]" /> 85% Threshold
                </span>
              </div>
            </div>

            {/* Warm Brown Bars & Orange Exceeded Highlight */}
            <div className="h-64 w-full flex items-end justify-between gap-3 pt-6 pb-2 px-4 bg-[#F0EDE8]/40 rounded-[6px] relative overflow-hidden">
              {/* Threshold Dotted Line (Alert state in #E8622A) */}
              <div
                className="absolute left-0 right-0 border-b-2 border-dashed border-[#E8622A]/70 z-0 pointer-events-none"
                style={{ bottom: '85%' }}
              >
                <span className="absolute right-3 -top-5 text-[10px] font-bold text-[#E8622A] bg-[#FAFAF8] px-2 py-0.5 rounded-[4px] shadow-xs">
                  Threshold 85%
                </span>
              </div>

              {[
                { day: 'Mon 07', pct: 68 },
                { day: 'Tue 08', pct: 79 },
                { day: 'Wed 09', pct: 88 }, // Exceeded -> Alert in #E8622A
                { day: 'Thu 10', pct: 92 }, // Exceeded -> Alert in #E8622A
                { day: 'Fri 11', pct: 75 },
                { day: 'Sat 12', pct: 24 },
                { day: 'Sun 13', pct: 15 },
              ].map((item) => {
                const isExceeded = item.pct > 85;
                return (
                  <div key={item.day} className="flex-1 flex flex-col items-center h-full justify-end z-10 group">
                    <span className="text-[10px] font-bold text-[#242424] mb-1">
                      {item.pct}%
                    </span>
                    <div
                      style={{ height: `${item.pct}%` }}
                      className={`w-full max-w-[42px] rounded-t-[4px] transition-all duration-300 ${
                        isExceeded
                          ? 'bg-[#E8622A] shadow-xs'
                          : 'bg-[#A89880] opacity-80 group-hover:opacity-100'
                      }`}
                    />
                    <span className="text-[10px] text-[#8A8070] mt-2 font-medium tracking-wide">
                      {item.day}
                    </span>
                  </div>
                );
              })}
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
                    <th className="py-3 px-3.5">Operating Windows</th>
                    <th className="py-3 px-3.5">Booked Minutes</th>
                    <th className="py-3 px-3.5">Utilization Rate</th>
                    <th className="py-3 px-3.5 rounded-r-[4px]">Threshold</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E5E0D8]/40">
                  {mockUtilizationMetrics.map((m) => {
                    const isExceeded = (m.utilizationPercent || 0) > m.resource.utilizationThreshold;
                    return (
                      <tr key={m.resource.id} className="hover:bg-[#F0EDE8]/30 transition-colors">
                        <td className="py-3.5 px-3.5 font-semibold text-[#242424]">{m.resource.name}</td>
                        <td className="py-3.5 px-3.5 text-[#8A8070]">{m.operatingMinutes} min</td>
                        <td className="py-3.5 px-3.5 text-[#8A8070]">{m.scheduledMinutes} min</td>
                        <td className="py-3.5 px-3.5">
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
                                style={{ width: `${Math.min(m.utilizationPercent || 0, 100)}%` }}
                                className={`h-full rounded-[4px] ${
                                  isExceeded ? 'bg-[#E8622A]' : 'bg-[#D4CFC9]'
                                }`}
                              />
                            </div>
                          </div>
                        </td>
                        <td className="py-3.5 px-3.5 text-[#8A8070] font-medium">
                          {m.resource.utilizationThreshold}%
                        </td>
                      </tr>
                    );
                  })}
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
              Alerts trigger when rolling 7-day booked ratio strictly exceeds the 85% threshold.
            </p>

            <div className="space-y-3">
              {alerts.map((a) => (
                <div
                  key={a.id}
                  className={`p-4 rounded-[6px] border flex flex-col gap-2 transition-all ${
                    a.status === 'OPEN'
                      ? 'border-[#E8622A]/40 bg-[#E8622A]/5'
                      : a.status === 'ACKNOWLEDGED'
                      ? 'border-[#F59E0B]/40 bg-[#F59E0B]/5'
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
                          ? 'bg-[#F59E0B] text-white'
                          : 'bg-[#22C55E] text-white'
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

                  {/* Actions */}
                  {a.status !== 'RESOLVED' && (
                    <div className="flex items-center gap-2 pt-2.5 border-t border-[#E5E0D8]/60 mt-1">
                      {a.status === 'OPEN' && (
                        <button
                          onClick={() => acknowledgeAlert(a.id)}
                          className="px-3 py-1 rounded-[6px] bg-[#FAFAF8] text-[#242424] shadow-card hover:bg-white text-[11px] font-semibold cursor-pointer"
                        >
                          Acknowledge
                        </button>
                      )}
                      <button
                        onClick={() => resolveAlert(a.id)}
                        className="px-3 py-1 rounded-[6px] bg-[#242424] hover:bg-black text-white text-[11px] font-semibold cursor-pointer"
                      >
                        Resolve Episode
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
