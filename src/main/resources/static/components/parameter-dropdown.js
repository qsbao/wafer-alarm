/**
 * Reusable ParameterDropdown React component.
 * Fetches parameters from /api/parameters and renders a <select>.
 *
 * Props:
 *   value     - currently selected parameter ID (number or null)
 *   onChange  - callback(parameterId) when selection changes
 *   disabled  - optional, disables the select
 *   showDisabled - optional, if true shows disabled parameters too (default: false)
 *   placeholder  - optional, placeholder text (default: "Select parameter...")
 *
 * Usage (in a Babel-transpiled script block):
 *   <ParameterDropdown value={selectedId} onChange={setSelectedId} />
 */
function ParameterDropdown({ value, onChange, disabled, showDisabled, placeholder }) {
  const [parameters, setParameters] = React.useState([]);

  React.useEffect(() => {
    fetch('/api/parameters')
      .then(res => res.json())
      .then(data => {
        const filtered = showDisabled ? data : data.filter(p => p.enabled);
        setParameters(filtered);
      })
      .catch(err => console.error('Failed to load parameters:', err));
  }, [showDisabled]);

  return React.createElement('select', {
    value: value || '',
    onChange: (e) => onChange(e.target.value ? Number(e.target.value) : null),
    disabled: disabled,
    style: { padding: '8px 10px', border: '1px solid #ddd', borderRadius: '4px', fontSize: '0.875rem' },
  }, [
    React.createElement('option', { key: '', value: '' }, placeholder || 'Select parameter...'),
    ...parameters.map(p =>
      React.createElement('option', { key: p.id, value: p.id },
        `${p.name}${p.unit ? ' (' + p.unit + ')' : ''}${p.area ? ' — ' + p.area : ''}`)
    ),
  ]);
}
