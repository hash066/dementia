from __future__ import annotations

import asyncio

from phone.gemma import tools
from phone.memory import upsert
from phone.memory.db import get_db


def test_execute_update_patient_profile_tool(client) -> None:
    call = tools.ToolCall(
        name="update_patient_profile",
        arguments={"field": "preferred_drink", "value": "tea"},
    )
    result = asyncio.run(tools.execute_tool(call, source="test"))
    assert result.ok is True
    assert upsert.get_setting(get_db().raw, "profile.preferred_drink") == "tea"


def test_execute_tag_object_location_tool(client) -> None:
    call = tools.ToolCall(
        name="tag_object_location",
        arguments={"object_label": "glasses", "location": "living room"},
    )
    result = asyncio.run(tools.execute_tool(call, source="test"))
    assert result.ok is True
    context = tools.read_context()
    assert any("glasses" in row["summary"].lower() and "living room" in row["summary"].lower() for row in context)
