from phone.gemma.tools import (
    ContextEntry,
    ToolCall as ContextToolCall,
    TOOL_SPECS,
    append_context,
    read_context,
    parse_tool_calls as parse_context_tool_calls,
    run_gemma_tools as run_context_tool_calls,
)

CONTEXT_TOOL_SPEC = TOOL_SPECS[0]
