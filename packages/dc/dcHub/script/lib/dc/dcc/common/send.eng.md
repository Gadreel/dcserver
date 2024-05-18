## dc_dcc_comm_send_SendEmail

Arguments:

``` json
{
	From: [defaults to config]

	ReplyTo:  [optional]

	To: "address; address; ..."

	- or -

	To: [ "address", "address", ... ]

	Path: "path to communicate script"

	Args: [any structure - data for the script]   

	Tags: [ "tag 1", "tag 2", ...  ]
}
```

## dc_dcc_comm_send_SendEmailConversational

Arguments:

``` json
{
	From: [defaults to config]

	ReplyTo:  [optional]

	To: "address; address; ..."

	- or -

	To: [ "address", "address", ... ]

	Cc: [string or list]

	Bcc: [string or list]

	Path: "path to communicate script"

	Args: [any structure - data for the script]   

	Tags: [ "tag 1", "tag 2", ...  ]
}
```

## dc_dcc_comm_send_SendSms

Arguments:

``` json
{
	To: "phone; phone; ..."

	- or -

	To: [ "phone", "phone", ... ]

	Path: "path to communicate script"

	Args: [any structure - data for the script]   

	Tags: [ "tag 1", "tag 2", ...  ]
}
```

## dc_dcc_comm_send_SendNotices

Rather than providing the format for the notice, look up each users preference for notices and utilize that. Only useful if your audience consists of dcUser records though.

Arguments:

``` json
{
	To: "user_id; user_id; ..."

	- or -

	To: [ "user_id", "user_id", ... ]

	Path: "path to communicate script"

	Args: [any structure - data for the script]   

	Tags: [ "tag 1", "tag 2", ...  ]
}
```

## Future

- SendSlack - message to channel or user
- SendPush - a push notification
