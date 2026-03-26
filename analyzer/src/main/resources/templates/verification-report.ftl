<#-- ═══════════════════════════════════════════════════════════════
     FreeMarker template — HTML Verification Report
     ═══════════════════════════════════════════════════════════════ -->

<#-- ── Macro: one violation card ─────────────────────────────── -->
<#macro violationCard v showInstance=true>
  <#assign cssType = v.type?lower_case?replace("_", "-")>
  <div class="violation-card">
    <div class="violation-card-header ${cssType}" onclick="toggleCard(this)">
      <div>
        <strong>#${v.eventIndex}</strong>
        <span class="tag ${cssType}">${v.type?replace("_"," ")}</span>
        <code>${v.className?html}.${v.methodName?html}()</code>
        <#if showInstance>
          &mdash; <code>${v.instanceId?html}</code>
        </#if>
      </div>
      <span class="chevron">&#9654;</span>
    </div>
    <div class="collapsible-content">
      <div class="violation-card-body">
        <@field label="Event index"  value=v.eventIndex?string />
        <@field label="Method"       value="${v.className?html}.${v.methodName?html}()" />
        <@field label="Timestamp"    value=v.timestamp?html />
        <@field label="Thread"       value=v.threadName?html />
        <@field label="Instance"     value=v.instanceId?html />
        <@field label="Caller class" value=v.callerClass?html />
        <@field label="Caller method" value=v.callerMethod?html />
        <@field label="Line number"  value=v.lineNum?html />
        <@field label="Current automaton state" value=v.currentState?html />
        <@field label="Trigger"      value=v.attemptedFunction?html />
        <@field label="Detail"       value=v.detail?html />
        <div class="field">
          <span class="field-label">Available</span>
          <span class="field-value">
            <#if v.availableFunctions?has_content>
              <#list v.availableFunctions as fn>
                <span class="tag available">${fn?html}</span>
              </#list>
            <#else>
              <em class="muted">none</em>
            </#if>
          </span>
        </div>
      </div>
    </div>
  </div>
</#macro>

<#-- ── Macro: key-value field row ────────────────────────────── -->
<#macro field label value>
  <div class="field">
    <span class="field-label">${label}</span>
    <span class="field-value">${value}</span>
  </div>
</#macro>

<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Verification Report &mdash; ${automatonName?html}</title>
  <style>
    /* ── reset ───────────────────────────────────────────────── */
    *{margin:0;padding:0;box-sizing:border-box}

    :root{
      --pass:#10b981;--fail:#ef4444;--warn:#f59e0b;--info:#3b82f6;
      --bg:#f8fafc;--card:#fff;--border:#e2e8f0;
      --text:#1e293b;--text2:#64748b;
      --shadow:0 1px 3px rgba(0,0,0,.1),0 1px 2px rgba(0,0,0,.06);
      --shadow-lg:0 10px 15px -3px rgba(0,0,0,.1);
      --r:12px;
    }

    body{
      font-family:'Segoe UI',system-ui,-apple-system,sans-serif;
      background:var(--bg);color:var(--text);line-height:1.6;padding:2rem
    }
    .container{max-width:1200px;margin:0 auto}
    code{font-family:'Cascadia Code','Fira Code',Consolas,monospace;font-size:.88em}
    .muted{color:var(--text2)}

    /* ── header ──────────────────────────────────────────────── */
    .header{
      background:linear-gradient(135deg,#1e293b,#334155);
      color:#fff;padding:2.5rem;border-radius:var(--r);
      margin-bottom:2rem;box-shadow:var(--shadow-lg);
      display:flex;flex-wrap:wrap;align-items:flex-start;
      justify-content:space-between;gap:1rem
    }
    .header h1{font-size:1.75rem;font-weight:700}
    .header .subtitle{color:#94a3b8;font-size:.9rem}
    .header .automaton-name{
      display:inline-block;background:rgba(255,255,255,.1);
      padding:.25rem .75rem;border-radius:6px;margin-top:.5rem;
      font-family:Consolas,'Fira Code',monospace
    }
    .result-badge{
      display:inline-flex;align-items:center;gap:.5rem;
      padding:.6rem 1.4rem;border-radius:50px;font-weight:700;font-size:1.1rem
    }
    .result-badge.pass{background:rgba(16,185,129,.15);color:var(--pass);border:2px solid var(--pass)}
    .result-badge.fail{background:rgba(239,68,68,.15);color:var(--fail);border:2px solid var(--fail)}

    /* ── stat cards ──────────────────────────────────────────── */
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:1rem;margin-bottom:2rem}
    .stat-card{
      background:var(--card);padding:1.5rem;border-radius:var(--r);
      box-shadow:var(--shadow);border-left:4px solid var(--info);transition:transform .2s
    }
    .stat-card:hover{transform:translateY(-2px)}
    .stat-card.ok{border-left-color:var(--pass)}
    .stat-card.bad{border-left-color:var(--fail)}
    .stat-card .label{font-size:.78rem;text-transform:uppercase;letter-spacing:.05em;color:var(--text2)}
    .stat-card .value{font-size:2rem;font-weight:700}
    .stat-card .sub{font-size:.8rem;color:var(--text2)}
    .progress-bar{height:8px;background:#e2e8f0;border-radius:4px;overflow:hidden;margin-top:.5rem}
    .progress-bar .fill{height:100%;border-radius:4px;transition:width .6s ease}
    .fill.green{background:var(--pass)} .fill.blue{background:var(--info)} .fill.red{background:var(--fail)}

    /* ── sections ────────────────────────────────────────────── */
    .section{background:var(--card);border-radius:var(--r);box-shadow:var(--shadow);margin-bottom:2rem;overflow:hidden}
    .section-header{
      padding:1.25rem 1.5rem;border-bottom:1px solid var(--border);
      display:flex;align-items:center;justify-content:space-between
    }
    .section-header h2{font-size:1.1rem;font-weight:600;display:flex;align-items:center;gap:.5rem}
    .section-body{padding:1.5rem}

    /* ── tables ──────────────────────────────────────────────── */
    table{width:100%;border-collapse:collapse}
    th{background:#f1f5f9;padding:.75rem 1rem;text-align:left;font-size:.78rem;
       text-transform:uppercase;letter-spacing:.05em;color:var(--text2);font-weight:600}
    td{padding:.75rem 1rem;border-top:1px solid var(--border);font-size:.9rem}
    tr:hover td{background:#f8fafc}

    /* ── violation cards ─────────────────────────────────────── */
    .violation-card{border:1px solid var(--border);border-radius:8px;margin-bottom:.75rem;overflow:hidden;transition:box-shadow .2s}
    .violation-card:hover{box-shadow:var(--shadow-lg)}
    .violation-card-header{
      padding:.85rem 1.25rem;display:flex;align-items:center;
      justify-content:space-between;cursor:pointer;user-select:none
    }
    .violation-card-header.no-transition{background:linear-gradient(135deg,#fef2f2,#fff1f2);border-bottom:2px solid var(--fail)}
    .violation-card-header.guard-failed{background:linear-gradient(135deg,#fffbeb,#fef3c7);border-bottom:2px solid var(--warn)}
    .violation-card-body{padding:1.25rem}
    .field{display:grid;grid-template-columns:150px 1fr;padding:.3rem 0;font-size:.9rem}
    .field-label{color:var(--text2);font-weight:500}
    .field-value{font-family:Consolas,'Fira Code',monospace;font-size:.85rem;word-break:break-all}

    /* ── tags / badges ───────────────────────────────────────── */
    .tag{display:inline-block;padding:.15rem .55rem;border-radius:4px;font-size:.75rem;font-weight:600;text-transform:uppercase}
    .tag.no-transition{background:#fef2f2;color:var(--fail)}
    .tag.guard-failed{background:#fffbeb;color:#b45309}
    .tag.available{background:#f0fdf4;color:#15803d;font-family:Consolas,monospace;text-transform:none;font-weight:400;margin:.15rem}
    .tag.info-tag{background:#e0f2fe;color:#0369a1}
    .count-badge{
      display:inline-flex;align-items:center;justify-content:center;
      min-width:1.5rem;height:1.5rem;padding:0 .4rem;border-radius:50px;
      background:var(--fail);color:#fff;font-size:.75rem;font-weight:700
    }

    /* ── tabs ─────────────────────────────────────────────────── */
    .tabs{display:flex;gap:0;border-bottom:2px solid var(--border);margin-bottom:1.5rem}
    .tab{
      padding:.75rem 1.25rem;cursor:pointer;font-weight:500;color:var(--text2);
      border:none;border-bottom:2px solid transparent;margin-bottom:-2px;
      transition:all .2s;background:none;font-size:.9rem
    }
    .tab:hover{color:var(--text)} .tab.active{color:var(--info);border-bottom-color:var(--info)}
    .tab-content{display:none} .tab-content.active{display:block}

    /* ── collapsible ─────────────────────────────────────────── */
    .collapsible-content{max-height:0;overflow:hidden;transition:max-height .35s ease}
    .collapsible-content.open{max-height:3000px}
    .chevron{transition:transform .3s;font-size:.8rem}
    .chevron.open{transform:rotate(90deg)}

    /* ── instance group ──────────────────────────────────────── */
    .instance-group{margin-bottom:1.5rem}
    .instance-group-header{
      font-weight:600;padding:.5rem 0;border-bottom:1px solid var(--border);
      margin-bottom:.75rem;display:flex;align-items:center;gap:.5rem
    }

    /* ── empty state ─────────────────────────────────────────── */
    .empty-state{text-align:center;padding:3rem;color:var(--text2)}
    .empty-state .icon{font-size:3rem;margin-bottom:1rem}

    /* ── footer ──────────────────────────────────────────────── */
    .footer{text-align:center;padding:2rem;color:var(--text2);font-size:.8rem}

    /* ── print ───────────────────────────────────────────────── */
    @media print{body{padding:0}.collapsible-content{max-height:none!important}.violation-card{break-inside:avoid}}
    @media(max-width:768px){body{padding:1rem}.stats-grid{grid-template-columns:repeat(2,1fr)}}
  </style>
</head>
<body>
<div class="container">

  <!-- ═══════════════════ HEADER ═══════════════════ -->
  <div class="header">
    <div>
      <h1>&#128203; Отчет о проверке интеграции</h1>
      <p class="subtitle">Отчет о проверке от ${generatedAt}</p>
      <div class="automaton-name">${automatonName?html}</div>
    </div>
    <#if isValid>
      <div class="result-badge pass">&#9989; PASS</div>
    <#else>
      <div class="result-badge fail">&#10060; FAIL</div>
    </#if>
  </div>

  <!-- ═══════════════════ STATS ═══════════════════ -->
  <div class="stats-grid">
    <div class="stat-card">
      <div class="label">Total Events</div>
      <div class="value">${totalEvents}</div>
      <div class="sub">in trace</div>
    </div>
    <div class="stat-card">
      <div class="label">Relevant Events</div>
      <div class="value">${relevantEvents}</div>
      <div class="sub">${relevantPercent}% of total</div>
      <div class="progress-bar"><div class="fill blue" style="width:${relevantPercent}%"></div></div>
    </div>
    <div class="stat-card ok">
      <div class="label">Successful Transitions</div>
      <div class="value">${successfulTransitions}</div>
      <div class="sub">${transitionSuccessRate}% success rate</div>
      <div class="progress-bar"><div class="fill green" style="width:${transitionSuccessRate}%"></div></div>
    </div>
    <div class="stat-card bad">
      <div class="label">Violations</div>
      <div class="value" style="color:<#if (violationCount>0)>var(--fail)<#else>var(--pass)</#if>">
        ${violationCount}
      </div>
      <div class="sub"><#if violationCount==0>no issues<#else>issues detected</#if></div>
    </div>
  </div>

  <!-- ═══════════════════ FINAL STATES ═══════════════════ -->
  <#if finalStates?has_content>
  <div class="section">
    <div class="section-header">
      <h2>&#127937; Final States</h2>
      <span class="tag info-tag">${finalStates?size} instance(s)</span>
    </div>
    <div class="section-body" style="padding:0">
      <table>
        <thead><tr><th>Instance ID</th><th>Final State</th></tr></thead>
        <tbody>
          <#list finalStates as fs>
          <tr>
            <td><code>${fs.instanceId?html}</code></td>
            <td><strong>${fs.state?html}</strong></td>
          </tr>
          </#list>
        </tbody>
      </table>
    </div>
  </div>
  </#if>

  <!-- ═══════════════════ VIOLATIONS ═══════════════════ -->
  <#if violations?has_content>
  <div class="section">
    <div class="section-header">
      <h2>&#9888;&#65039; Violations <span class="count-badge">${violationCount}</span></h2>
    </div>
    <div class="section-body">

      <!-- ── tabs ────────────────────────────────────── -->
      <div class="tabs">
        <button class="tab active" onclick="switchTab(event,'all')">All</button>
        <button class="tab"        onclick="switchTab(event,'by-instance')">By Instance</button>
        <button class="tab"        onclick="switchTab(event,'by-type')">By Type</button>
        <button class="tab"        onclick="switchTab(event,'table')">Table View</button>
      </div>

      <!-- TAB: all -->
      <div id="tab-all" class="tab-content active">
        <#list violations as v><@violationCard v=v/></#list>
      </div>

      <!-- TAB: by instance -->
      <div id="tab-by-instance" class="tab-content">
        <#list violationsByInstance as instId, instList>
        <div class="instance-group">
          <div class="instance-group-header">
            <code>${instId?html}</code>
            <span class="count-badge">${instList?size}</span>
          </div>
          <#list instList as v><@violationCard v=v showInstance=false/></#list>
        </div>
        </#list>
      </div>

      <!-- TAB: by type -->
      <div id="tab-by-type" class="tab-content">
        <#list violationsByType as typeName, typeList>
        <div class="instance-group">
          <div class="instance-group-header">
            <span class="tag ${typeName?lower_case?replace('_','-')}">${typeName}</span>
            <span class="count-badge">${typeList?size}</span>
          </div>
          <#list typeList as v><@violationCard v=v/></#list>
        </div>
        </#list>
      </div>

      <!-- TAB: table -->
      <div id="tab-table" class="tab-content">
        <div style="overflow-x:auto">
          <table>
            <thead>
              <tr><th>#</th><th>Type</th><th>Method</th><th>Instance</th>
                  <th>State</th><th>Trigger</th><th>Line</th></tr>
            </thead>
            <tbody>
              <#list violations as v>
              <tr>
                <td>${v.eventIndex}</td>
                <td><span class="tag ${v.type?lower_case?replace('_','-')}">${v.type?replace("_"," ")}</span></td>
                <td><code>${v.className?html}.${v.methodName?html}()</code></td>
                <td><code>${v.instanceId?html}</code></td>
                <td>${v.currentState?html}</td>
                <td><code>${v.attemptedFunction?html}</code></td>
                <td>${v.lineNum?html}</td>
              </tr>
              </#list>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  </div>

  <#else>
  <!-- ── no violations ──────────────────────────────── -->
  <div class="section">
    <div class="section-body">
      <div class="empty-state">
        <div class="icon">&#127881;</div>
        <h3>No Violations Found</h3>
        <p>The trace fully conforms to the <strong>${automatonName?html}</strong> specification.</p>
      </div>
    </div>
  </div>
  </#if>

  <!-- ═══════════════════ FOOTER ═══════════════════ -->
  <div class="footer">
    Generated&nbsp;${generatedAt} &mdash; © Polyakov Andrey, 2026
  </div>

</div>

<script>
function switchTab(e,id){
  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(c=>c.classList.remove('active'));
  e.currentTarget.classList.add('active');
  document.getElementById('tab-'+id).classList.add('active');
}
function toggleCard(hdr){
  hdr.nextElementSibling.classList.toggle('open');
  hdr.querySelector('.chevron').classList.toggle('open');
}
</script>
</body>
</html>